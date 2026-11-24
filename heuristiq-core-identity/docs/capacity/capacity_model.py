#!/usr/bin/env python3
"""Capacity calculator for Rider_UAM. Standard library only.

    python capacity_model.py scenario.json            # human readable report
    python capacity_model.py scenario.json --json     # same numbers as JSON
    python capacity_model.py scenario.json --no-uam-properties

Everything printed is DERIVED FROM THE PARAMETERS in scenario.json. Nothing here is a measurement.
Leaves of the scenario file carry a `basis` (CODE, USER, ASSUMPTION, PLACEHOLDER); the report ends with a
register of every PLACEHOLDER so it is obvious which numbers must be replaced by load-test results first.
"""
import argparse
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
UAM_RESOURCES = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources"))

ENDPOINTS = ["login", "refresh", "logout", "me_privileges", "me_organizations",
             "user_lookup", "user_list", "user_search"]
ENDPOINT_PATHS = {
    "login": "POST /identity/auth/login",
    "refresh": "POST /identity/auth/refresh",
    "logout": "POST /identity/auth/logout",
    "me_privileges": "GET /identity/me/privileges",
    "me_organizations": "GET /identity/me/organizations",
    "user_lookup": "GET /users/{id}|by-phone|batch",
    "user_list": "GET /users?page=",
    "user_search": "GET /users?search=",
}


# --------------------------------------------------------------------------- parameter access

def v(node):
    """Value of a parameter node: {"v": x, ...} -> x, anything else is returned unchanged."""
    if isinstance(node, dict) and "v" in node:
        return node["v"]
    return node


def walk(node, path=""):
    """Yield (path, node) for every parameter node (a dict that has a "v" and a "basis")."""
    if isinstance(node, dict):
        if "v" in node and "basis" in node:
            yield path, node
            return
        for key, child in node.items():
            yield from walk(child, f"{path}.{key}" if path else key)
    elif isinstance(node, list):
        for i, child in enumerate(node):
            yield from walk(child, f"{path}[{i}]")


_PROP_DEFAULT = re.compile(r"^\$\{[^:}]+:([^}]*)\}$")


def read_property(spec):
    """spec = 'file.properties:the.key' -> default value in UAM's committed properties, or None."""
    fname, key = spec.split(":", 1)
    path = os.path.join(UAM_RESOURCES, fname)
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, _, val = line.partition("=")
            if k.strip() != key:
                continue
            val = val.strip()
            m = _PROP_DEFAULT.match(val)
            val = m.group(1) if m else val
            try:
                return float(val) if "." in val else int(val)
            except ValueError:
                return val
    return None


def apply_uam_properties(scenario, notes):
    for path, node in walk(scenario):
        spec = node.get("read_from")
        if not spec:
            continue
        found = read_property(spec)
        if found is None:
            notes.append(f"{path}: could not read {spec}, using scenario value {node['v']}")
            continue
        if found != node["v"]:
            notes.append(f"{path}: scenario value {node['v']} replaced by {found} read from {spec}")
        else:
            notes.append(f"{path}: {found} (confirmed against {spec})")
        node["v"] = found


# --------------------------------------------------------------------------- model

def ceil_div(a, b):
    return int(math.ceil(a / b)) if b > 0 else 0


def gateway_status_lookups(sc, active_users, window_minutes):
    """Reads of uam_users by primary key that the GATEWAY makes (it reads UAM's database directly).

    Model: each gateway instance holds a Caffeine cache (size C, TTL T) keyed by user id. With N distinct
    users per instance sending R requests/s in total:
      N <= C: every user is refreshed at most once per TTL           -> misses/s = min(R, N / T)
      N  > C: uniform access, so the hit ratio is about C / N         -> misses/s = R * (1 - C / N)
    This is an approximation (the real eviction policy is W-TinyLFU), good for an order of magnitude.
    """
    g = sc["gateway"]
    instances = v(g["instances"])
    per_user_hour = v(g["requests_per_active_user_per_hour"])
    ttl = v(g["user_status_ttl_seconds"])
    cache = v(g["user_status_cache_max_size"])
    distinct = active_users / instances
    rate = distinct * per_user_hour / 3600.0
    misses = min(rate, distinct / ttl) if distinct <= cache else rate * (1.0 - cache / distinct)
    return {"gateway_requests_per_s_per_instance": rate,
            "distinct_users_per_instance": distinct,
            "cache_size": cache,
            "user_status_db_reads_per_s_total": misses * instances}


def endpoint_counts(sc, active_users, window):
    """Requests per endpoint inside the window (not per second)."""
    minutes = v(window["minutes"])
    hours = minutes / 60.0
    pau = sc["per_active_user"]
    ttl_min = v(sc["uam_config"]["access_ttl_minutes"])
    staff = sc["staff"]
    staff_active = v(staff["active_staff_in_window"])
    return {
        "login": active_users * v(window["login_fraction"]),
        "refresh": active_users * v(pau["refresh_participation"]) * (minutes / ttl_min),
        "logout": active_users * v(pau["logout_per_hour"]) * hours,
        "me_privileges": active_users * v(pau["me_privileges_per_hour"]) * hours,
        "me_organizations": active_users * v(pau["me_organizations_per_hour"]) * hours,
        "user_lookup": active_users * v(pau["user_lookup_per_hour"]) * hours,
        "user_list": staff_active * v(staff["list_calls_per_staff_per_hour"]) * hours,
        "user_search": staff_active * v(staff["search_calls_per_staff_per_hour"]) * hours,
    }


def size_window(sc, active_users, window):
    seconds = v(window["minutes"]) * 60.0
    peak = v(sc["load_shape"]["intra_window_peak_factor"])
    counts = endpoint_counts(sc, active_users, window)
    avg_rps = {e: counts[e] / seconds for e in ENDPOINTS}
    peak_rps = {e: avg_rps[e] * peak for e in ENDPOINTS}
    total_avg = sum(avg_rps.values())
    total_peak = sum(peak_rps.values())

    cost = sc["cost_per_request"]
    inst = sc["instance"]
    cores = v(inst["cores"])
    util = v(inst["cpu_target_utilization"])
    headroom = v(inst["headroom"])
    login_ms = v(cost["login_cpu_ms"])
    other_ms = v(cost["other_cpu_ms"])

    # CPU: logins are bcrypt-bound, everything else is cheap.
    cpu_cores_needed = (peak_rps["login"] * login_ms + (total_peak - peak_rps["login"]) * other_ms) / 1000.0
    instances_by_cpu = ceil_div(cpu_cores_needed, cores * util)
    max_login_rps_per_instance = cores * util / (login_ms / 1000.0)

    # Database connections in use (Little's law): sum(rps_e x hold_e).
    holds = {e: v(cost["db_hold_ms"][e]) for e in ENDPOINTS}
    conns_in_use = sum(peak_rps[e] * holds[e] / 1000.0 for e in ENDPOINTS)

    pool = v(sc["uam_config"]["db_pool_size_per_instance"])
    pg = sc["postgres"]
    pg_for_uam = (v(pg["max_connections"]) - v(pg["connections_reserved_for_others"])) * v(pg["usable_fraction"])
    max_instances_by_pg = int(pg_for_uam // pool) if pool else 0
    instances_by_pool = ceil_div(conns_in_use, pool)

    rows = []
    for rps in inst["sustainable_rps_table"]:
        by_rps = ceil_div(total_peak, rps * (1.0 - headroom))
        needed = max(by_rps, instances_by_cpu, instances_by_pool)
        rows.append({
            "sustainable_rps_per_instance": rps,
            "instances_by_rps": by_rps,
            "instances_required": needed,
            "db_connections_if_pools_full": needed * pool,
            "fits_postgres": needed * pool <= pg_for_uam,
        })
    measured = v(inst["sustainable_rps_per_instance"])
    measured_row = None
    if measured:
        by_rps = ceil_div(total_peak, measured * (1.0 - headroom))
        needed = max(by_rps, instances_by_cpu, instances_by_pool)
        measured_row = {"sustainable_rps_per_instance": measured, "instances_by_rps": by_rps,
                        "instances_required": needed, "db_connections_if_pools_full": needed * pool,
                        "fits_postgres": needed * pool <= pg_for_uam}

    return {
        "active_users": active_users,
        "window_minutes": v(window["minutes"]),
        "counts": counts,
        "avg_rps": avg_rps,
        "peak_rps": peak_rps,
        "total_avg_rps": total_avg,
        "total_peak_rps": total_peak,
        "cpu_cores_needed": cpu_cores_needed,
        "instances_by_cpu": instances_by_cpu,
        "max_login_rps_per_instance": max_login_rps_per_instance,
        "db_connections_in_use": conns_in_use,
        "instances_by_db_pool": instances_by_pool,
        "pg_connections_available_for_uam": pg_for_uam,
        "max_instances_by_postgres": max_instances_by_pg,
        "table": rows,
        "measured_row": measured_row,
        "gateway": gateway_status_lookups(sc, active_users, v(window["minutes"])),
        "login_queue": {
            "bcrypt_workers_per_instance": cores,
            "queue_capacity": v(sc["uam_config"]["hash_pool_queue_capacity"]),
            "timeout_ms": v(sc["uam_config"]["hash_timeout_ms"]),
        },
    }


def mix_percent(result):
    total = result["total_avg_rps"]
    return {e: 100.0 * result["avg_rps"][e] / total for e in ENDPOINTS} if total else {}


def storage(sc, dau):
    pop = sc["population"]
    registered = v(pop["registered_users"])
    ev = sc["storage"]["event_rates"]
    ttl = v(sc["uam_config"]["access_ttl_minutes"])
    logins = dau * v(ev["logins_per_dau_per_day"])
    refreshes = dau * v(ev["active_minutes_per_dau_per_day"]) / ttl
    failures = dau * v(ev["failed_attempts_per_dau_per_day"])
    ever = registered * v(pop["ever_logged_in_pct"]) / 100.0
    daily = {
        "registered": None,
        "ever_logged_in": None,
        "devices": None,
        "daily_logins": logins,
        "daily_logins_plus_refreshes": logins + refreshes,
        "daily_logins_plus_failures": logins + failures,
        "daily_failures": failures,
    }
    growth = v(pop["registration_growth_pct_per_year"]) / 100.0
    retention = v(sc["storage"]["proposed_retention_days"])
    out = []
    for t in sc["storage"]["tables"]:
        basis = t["rows"]
        row_bytes = t["heap_bytes"] + t["index_bytes"]
        if basis in ("registered", "ever_logged_in", "devices"):
            base = {"registered": registered, "ever_logged_in": ever,
                    "devices": ever * v(pop["devices_per_logged_in_user"])}[basis]
            rows_now = base
            rows_growth_year = base * growth
            rows_unbounded_5y = base * (1 + growth) ** 5
            rows_with_retention = rows_unbounded_5y
            per_day = None
        else:
            per_day = daily[basis]
            rows_now = per_day * 365          # one year of the current (non-purging) behaviour
            rows_growth_year = per_day * 365
            rows_unbounded_5y = per_day * 365 * 5
            days = retention.get(t["name"])
            rows_with_retention = per_day * days if days else rows_unbounded_5y
        out.append({
            "name": t["name"], "basis": basis, "row_bytes": row_bytes,
            "rows_after_1y_or_now": rows_now,
            "gb_after_1y_or_now": rows_now * row_bytes / 1e9,
            "growth_gb_per_year": rows_growth_year * row_bytes / 1e9,
            "gb_after_5y_unbounded": rows_unbounded_5y * row_bytes / 1e9,
            "gb_with_retention": rows_with_retention * row_bytes / 1e9,
            "per_day_rows": per_day,
        })
    return out


def working_set(sc, active_users):
    """RAM the login path needs to stay cached. Two bounds, both estimates built from the byte parameters."""
    tabs = {t["name"]: t for t in sc["storage"]["tables"]}
    registered = v(sc["population"]["registered_users"])
    page = v(sc["storage"]["working_set"]["page_size_bytes"])
    login_tables = ["uam_users", "uam_user_roles", "user_credentials", "tenant_memberships", "user_login_security"]
    index_total = sum(registered * tabs[n]["index_bytes"] for n in login_tables if n in tabs)
    heap_clustered = sum(active_users * tabs[n]["heap_bytes"] for n in login_tables if n in tabs)
    heap_scattered = sum(min(active_users * page, registered * tabs[n]["heap_bytes"])
                         for n in login_tables if n in tabs)
    return {
        "indexes_gb_all_users": index_total / 1e9,
        "heap_gb_if_active_rows_are_clustered": heap_clustered / 1e9,
        "heap_gb_if_one_page_per_active_row": heap_scattered / 1e9,
        "low_gb": (index_total + heap_clustered) / 1e9,
        "high_gb": (index_total + heap_scattered) / 1e9,
    }


def build(sc, use_props):
    notes = []
    if use_props:
        apply_uam_properties(sc, notes)
    registered = v(sc["population"]["registered_users"])
    result = {"uam_properties_notes": notes, "scenarios": {}, "storage": {}, "working_set": {}}
    for name, spec in sc["peak_scenarios"].items():
        active = registered * v(spec["peak_active_pct"]) / 100.0
        result["scenarios"][name] = {
            "peak_active_pct": v(spec["peak_active_pct"]),
            "windows": {w: size_window(sc, active, win) for w, win in sc["windows"].items()},
        }
        result["working_set"][name] = working_set(sc, active)
    for label, key in (("dau_low", "daily_active_pct_low"), ("dau_high", "daily_active_pct_high")):
        dau = registered * v(sc["population"][key]) / 100.0
        result["storage"][label] = {"dau": dau, "tables": storage(sc, dau)}
    return result


# --------------------------------------------------------------------------- report

def fmt_users(n):
    return f"{n / 1e6:.2f}M"


def report(sc, res):
    out = []
    p = out.append
    p("CAPACITY MODEL FOR RIDER_UAM  (parameter-driven; NOT a measurement)")
    p("=" * 72)
    reg = v(sc["population"]["registered_users"])
    p(f"registered users: {reg:,}   daily active: "
      f"{v(sc['population']['daily_active_pct_low'])}-{v(sc['population']['daily_active_pct_high'])}%")
    p("")
    p("UAM settings used (CODE = defaults in committed properties; a deployment can override):")
    for line in res["uam_properties_notes"]:
        p("  " + line)
    p(f"  sustainable_rps_per_instance = {v(sc['instance']['sustainable_rps_per_instance'])}  "
      f"(PLACEHOLDER: null until measured)")
    p("")

    for name, sres in res["scenarios"].items():
        for wname, w in sres["windows"].items():
            p("-" * 72)
            p(f"SCENARIO {name.upper()}: {sres['peak_active_pct']}% active = {fmt_users(w['active_users'])} users, "
              f"window {wname} ({w['window_minutes']} min)")
            p("-" * 72)
            p(f"  {'endpoint':<18}{'requests in window':>20}{'avg rps':>12}{'peak rps':>12}")
            for e in ENDPOINTS:
                p(f"  {e:<18}{w['counts'][e]:>20,.0f}{w['avg_rps'][e]:>12.1f}{w['peak_rps'][e]:>12.1f}")
            p(f"  {'TOTAL':<18}{sum(w['counts'].values()):>20,.0f}{w['total_avg_rps']:>12.1f}{w['total_peak_rps']:>12.1f}")
            p(f"  CPU cores needed at peak (login_cpu_ms/other_cpu_ms PLACEHOLDERS): {w['cpu_cores_needed']:.1f}"
              f"  -> instances by CPU: {w['instances_by_cpu']}")
            p(f"  max logins/s one instance can take (cores x util / login_cpu): {w['max_login_rps_per_instance']:.1f}"
              f"   (bcrypt queue {w['login_queue']['queue_capacity']}, timeout {w['login_queue']['timeout_ms']} ms)")
            p(f"  DB connections in use at peak (Little's law, hold-time PLACEHOLDERS): {w['db_connections_in_use']:.1f}"
              f"  -> instances to cover with pool size {v(sc['uam_config']['db_pool_size_per_instance'])}: {w['instances_by_db_pool']}")
            p(f"  Postgres connections available to UAM: {w['pg_connections_available_for_uam']:.0f}"
              f"  -> at most {w['max_instances_by_postgres']} instances before max_connections is the limit")
            g = w["gateway"]
            p(f"  Gateway user-status reads of uam_users (PK lookups, NOT UAM requests): "
              f"{g['user_status_db_reads_per_s_total']:.0f}/s "
              f"({g['distinct_users_per_instance']:,.0f} distinct users per gateway instance vs cache {g['cache_size']:,})")
            p("")
            p("  Instances required as a function of the UNKNOWN sustainable rps per instance:")
            p(f"  {'rps/instance':>13}{'by rps':>9}{'required':>10}{'DB conns':>10}  fits Postgres")
            for r in w["table"]:
                p(f"  {r['sustainable_rps_per_instance']:>13}{r['instances_by_rps']:>9}{r['instances_required']:>10}"
                  f"{r['db_connections_if_pools_full']:>10}  {'yes' if r['fits_postgres'] else 'NO'}")
            if w["measured_row"]:
                r = w["measured_row"]
                p(f"  with sustainable_rps_per_instance={r['sustainable_rps_per_instance']}: "
                  f"{r['instances_required']} instances")
            p("  required = max(by rps, by CPU, by DB pool)")
            p("")

    p("=" * 72)
    p("SUMMARY - instances required vs the UNKNOWN sustainable rps per instance (parameter-driven, NOT measured)")
    p("=" * 72)
    rps_list = sc["instance"]["sustainable_rps_table"]
    head = f"{'scenario / window':<30}{'users':>7}{'peak rps':>10}{'logins/s':>10}{'by CPU':>8}" + "".join(f"{r:>7}" for r in rps_list)
    p(head)
    p(f"{'':<65}" + "  <- instances required at that sustainable rps per instance")
    for name, sres in res["scenarios"].items():
        for wname, w in sres["windows"].items():
            by_rps = {r["sustainable_rps_per_instance"]: r["instances_required"] for r in w["table"]}
            label = f"{name} {sres['peak_active_pct']}% / {wname}"
            p(f"{label:<30}{fmt_users(w['active_users']):>7}{w['total_peak_rps']:>10.0f}{w['peak_rps']['login']:>10.0f}"
              f"{w['instances_by_cpu']:>8}" + "".join(f"{by_rps[r]:>7}" for r in rps_list))
    p("  required = max(ceil(peak rps / (rps x (1 - headroom))), instances by CPU, instances by DB pool)")
    p(f"  headroom {v(sc['instance']['headroom'])}, cpu target {v(sc['instance']['cpu_target_utilization'])}, "
      f"cores/instance {v(sc['instance']['cores'])}, login_cpu_ms {v(sc['cost_per_request']['login_cpu_ms'])} (PLACEHOLDER), "
      f"pool/instance {v(sc['uam_config']['db_pool_size_per_instance'])}")
    p("")

    p("=" * 72)
    p("STORAGE (bytes per row are ASSUMPTIONS: measure with pg_total_relation_size on the seeded database)")
    p("=" * 72)
    for label, st in res["storage"].items():
        p(f"{label}: DAU = {fmt_users(st['dau'])}")
        p(f"  {'table':<24}{'B/row':>7}{'rows (now or after 1y)':>24}{'GB then':>9}{'GB/yr':>8}{'GB 5y unbounded':>17}{'GB w/ retention':>17}")
        tot = [0.0, 0.0, 0.0, 0.0]
        for t in st["tables"]:
            p(f"  {t['name']:<24}{t['row_bytes']:>7}{t['rows_after_1y_or_now']:>24,.0f}{t['gb_after_1y_or_now']:>9.1f}"
              f"{t['growth_gb_per_year']:>8.1f}{t['gb_after_5y_unbounded']:>17.1f}{t['gb_with_retention']:>17.1f}")
            tot[0] += t["gb_after_1y_or_now"]
            tot[1] += t["growth_gb_per_year"]
            tot[2] += t["gb_after_5y_unbounded"]
            tot[3] += t["gb_with_retention"]
        p(f"  {'TOTAL':<24}{'':>7}{'':>24}{tot[0]:>9.1f}{tot[1]:>8.1f}{tot[2]:>17.1f}{tot[3]:>17.1f}")
        p("")
    p("WORKING SET of the login path (indexes of all users + heap of the active users), GB:")
    ram = v(sc["postgres"]["ram_gb"])
    for name, ws in res["working_set"].items():
        p(f"  {name:<9} indexes {ws['indexes_gb_all_users']:.1f} + heap {ws['heap_gb_if_active_rows_are_clustered']:.1f}"
          f"..{ws['heap_gb_if_one_page_per_active_row']:.1f}  => {ws['low_gb']:.1f} .. {ws['high_gb']:.1f} GB"
          f"  (database RAM placeholder {ram} GB)")
    p("")

    exp = res["scenarios"].get("expected")
    if exp:
        mix = mix_percent(exp["windows"]["peak_hour"])
        configured = sc.get("loadtest", {}).get("mix", {})
        p("Request mix implied by 'expected' / peak_hour (share of requests, %), vs loadtest.mix in scenario.json:")
        drift = []
        for e in ENDPOINTS:
            p(f"  {e:<18}{mix.get(e, 0):>7.1f}   configured {configured.get(e, '-')}")
            if e in configured and abs(configured[e] - mix.get(e, 0)) > 2.0:
                drift.append(e)
        if drift:
            p("  WARNING: loadtest.mix differs by more than 2 points for: " + ", ".join(drift))
        p("")

    p("PLACEHOLDERS that must be replaced by measurements before the numbers above are used for sizing:")
    for path, node in walk(sc):
        if node.get("basis") == "PLACEHOLDER":
            p(f"  {path} = {node['v']}")
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("scenario", help="path to scenario.json")
    ap.add_argument("--json", action="store_true", help="print the results as JSON instead of the report")
    ap.add_argument("--no-uam-properties", action="store_true",
                    help="do not read TTLs/pool size from Rider_UAM/src/main/resources, use scenario.json values")
    args = ap.parse_args()
    with open(args.scenario, encoding="utf-8") as fh:
        sc = json.load(fh)
    res = build(sc, use_props=not args.no_uam_properties)
    if args.json:
        json.dump(res, sys.stdout, indent=2)
        print()
    else:
        print(report(sc, res))


if __name__ == "__main__":
    main()
