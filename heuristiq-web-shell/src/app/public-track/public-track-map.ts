import { Injectable } from '@angular/core';
import type * as Leaflet from 'leaflet';
import {
  PUBLIC_TRACK_DEFAULT_CENTER,
  PUBLIC_TRACK_DEFAULT_ZOOM,
  PUBLIC_TRACK_DROP_COLOR,
  PUBLIC_TRACK_FIT_PADDING,
  PUBLIC_TRACK_FOCUS_ZOOM,
  PUBLIC_TRACK_PICKUP_COLOR,
  PUBLIC_TRACK_POINT_RADIUS,
  PUBLIC_TRACK_TILE_ATTRIBUTION,
  PUBLIC_TRACK_TILE_MAX_ZOOM,
  PUBLIC_TRACK_TILE_REFERRER_POLICY,
  PUBLIC_TRACK_TILE_URL,
} from './public-track.constants';
import { PublicTrackMap, TrackLatLng, TrackMapView } from './public-track.models';

/** The Leaflet functions this page uses (the real module, or a stand-in in tests). */
export type LeafletApi = Pick<typeof Leaflet, 'map' | 'tileLayer' | 'marker' | 'divIcon' | 'circleMarker' | 'latLngBounds'>;

/** Car marker HTML: a Bootstrap icon turned to the driver's heading. */
export function carIconHtml(heading: number | null): string {
  const rotate = heading === null ? 0 : Math.round(heading);
  return `<span class="public-track-car__icon" style="transform: rotate(${rotate}deg)"><i class="bi bi-car-front-fill"></i></span>`;
}

/** Leaflet map with the car, the pickup and the drop. */
export class LeafletTrackMap implements PublicTrackMap {
  private readonly map: Leaflet.Map;
  private car: Leaflet.Marker | null = null;
  private pickup: Leaflet.CircleMarker | null = null;
  private drop: Leaflet.CircleMarker | null = null;

  constructor(private readonly L: LeafletApi, host: HTMLElement) {
    this.map = L.map(host, { zoomControl: true, attributionControl: true }).setView(
      [PUBLIC_TRACK_DEFAULT_CENTER[0], PUBLIC_TRACK_DEFAULT_CENTER[1]],
      PUBLIC_TRACK_DEFAULT_ZOOM,
    );
    L.tileLayer(PUBLIC_TRACK_TILE_URL, {
      attribution: PUBLIC_TRACK_TILE_ATTRIBUTION,
      maxZoom: PUBLIC_TRACK_TILE_MAX_ZOOM,
      referrerPolicy: PUBLIC_TRACK_TILE_REFERRER_POLICY,
    }).addTo(this.map);
  }

  /** Moves the markers to [view]; with [fit] the map zooms to show all of them. */
  update(view: TrackMapView, fit: boolean): void {
    this.pickup = this.placePoint(this.pickup, view.pickup, PUBLIC_TRACK_PICKUP_COLOR, view.pickupLabel);
    this.drop = this.placePoint(this.drop, view.drop, PUBLIC_TRACK_DROP_COLOR, view.dropLabel);
    this.placeCar(view.car, view.heading);
    if (fit) this.fit(view);
  }

  invalidateSize(): void {
    this.map.invalidateSize();
  }

  destroy(): void {
    this.map.remove();
  }

  private placeCar(point: TrackLatLng | null, heading: number | null): void {
    if (!point) {
      this.car?.remove();
      this.car = null;
      return;
    }
    const icon = this.L.divIcon({ className: 'public-track-car', html: carIconHtml(heading), iconSize: [36, 36], iconAnchor: [18, 18] });
    if (this.car) {
      this.car.setLatLng([point[0], point[1]]);
      this.car.setIcon(icon);
    } else {
      this.car = this.L.marker([point[0], point[1]], { icon, keyboard: false }).addTo(this.map);
    }
  }

  private placePoint(
    existing: Leaflet.CircleMarker | null,
    point: TrackLatLng | null,
    color: string,
    label: string | null,
  ): Leaflet.CircleMarker | null {
    if (!point) {
      existing?.remove();
      return null;
    }
    if (existing) {
      existing.setLatLng([point[0], point[1]]);
      return existing;
    }
    const marker = this.L.circleMarker([point[0], point[1]], {
      radius: PUBLIC_TRACK_POINT_RADIUS,
      color: '#ffffff',
      weight: 3,
      fillColor: color,
      fillOpacity: 1,
    });
    // Labels are plain text from the API: bound as text content, never as HTML.
    if (label) {
      const content = document.createElement('span');
      content.textContent = label;
      marker.bindTooltip(content, { direction: 'top' });
    }
    return marker.addTo(this.map);
  }

  private fit(view: TrackMapView): void {
    const points = [view.car, view.pickup, view.drop].filter((p): p is TrackLatLng => p !== null);
    if (points.length === 0) return;
    if (points.length === 1) {
      this.map.setView([points[0][0], points[0][1]], PUBLIC_TRACK_FOCUS_ZOOM);
      return;
    }
    this.map.fitBounds(this.L.latLngBounds(points.map((p) => [p[0], p[1]] as Leaflet.LatLngTuple)), {
      padding: [PUBLIC_TRACK_FIT_PADDING, PUBLIC_TRACK_FIT_PADDING],
      maxZoom: PUBLIC_TRACK_FOCUS_ZOOM,
    });
  }
}

/** Loads Leaflet only when the tracking page opens (it is not part of the main bundle). */
export async function loadLeaflet(): Promise<LeafletApi> {
  const mod = (await import('leaflet')) as typeof Leaflet & { default?: typeof Leaflet };
  return mod.default ?? mod;
}

/** Creates the tracking map; a service so the page can be tested without Leaflet. */
@Injectable({ providedIn: 'root' })
export class PublicTrackMapFactory {
  async create(host: HTMLElement): Promise<PublicTrackMap> {
    return new LeafletTrackMap(await loadLeaflet(), host);
  }
}
