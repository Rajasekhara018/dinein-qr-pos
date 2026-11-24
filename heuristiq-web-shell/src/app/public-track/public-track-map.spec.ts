import { vi } from 'vitest';
import { LeafletApi, LeafletTrackMap, PublicTrackMapFactory, carIconHtml, loadLeaflet } from './public-track-map';
import { PUBLIC_TRACK_FOCUS_ZOOM, PUBLIC_TRACK_TILE_REFERRER_POLICY } from './public-track.constants';
import { TrackMapView } from './public-track.models';

// Leaflet needs a real browser surface; the fake records what the adapter asked of it.
function fakeLeaflet() {
  const map = { setView: vi.fn(), fitBounds: vi.fn(), invalidateSize: vi.fn(), remove: vi.fn() };
  map.setView.mockReturnValue(map);
  const layer = () => {
    const l: Record<string, ReturnType<typeof vi.fn>> = {};
    for (const name of ['addTo', 'setLatLng', 'setIcon', 'remove', 'bindTooltip']) l[name] = vi.fn(() => l);
    return l;
  };
  const markers: Array<Record<string, ReturnType<typeof vi.fn>>> = [];
  const points: Array<Record<string, ReturnType<typeof vi.fn>>> = [];
  const tiles = { addTo: vi.fn() };
  const api = {
    map: vi.fn(() => map),
    tileLayer: vi.fn(() => tiles),
    marker: vi.fn(() => { const m = layer(); markers.push(m); return m; }),
    divIcon: vi.fn((o: unknown) => ({ icon: o })),
    circleMarker: vi.fn(() => { const p = layer(); points.push(p); return p; }),
    latLngBounds: vi.fn((p: unknown) => ({ bounds: p })),
  };
  return { api, map, markers, points, tiles };
}

const full: TrackMapView = { car: [12.9, 77.6], heading: 45, pickup: [12.8, 77.5], drop: [13, 77.7], pickupLabel: '<b>Gate</b>', dropLabel: null };

describe('LeafletTrackMap', () => {
  it('creates the map with OSM tiles that send the origin only', () => {
    const f = fakeLeaflet();
    new LeafletTrackMap(f.api as unknown as LeafletApi, document.createElement('div'));
    expect(f.api.map).toHaveBeenCalled();
    const [, options] = f.api.tileLayer.mock.calls[0] as unknown as [string, { referrerPolicy: string }];
    expect(options.referrerPolicy).toBe(PUBLIC_TRACK_TILE_REFERRER_POLICY);
    expect(f.tiles.addTo).toHaveBeenCalledWith(f.map);
  });

  it('draws car, pickup and drop, fits once, then moves the same markers', () => {
    const f = fakeLeaflet();
    const m = new LeafletTrackMap(f.api as unknown as LeafletApi, document.createElement('div'));
    m.update(full, true);
    expect(f.api.marker).toHaveBeenCalledTimes(1);
    expect(f.api.circleMarker).toHaveBeenCalledTimes(2);
    expect(f.map.fitBounds).toHaveBeenCalledTimes(1);
    // the label is bound as text, never parsed as HTML
    const tooltip = f.points[0]['bindTooltip'].mock.calls[0][0] as HTMLElement;
    expect(tooltip.textContent).toBe('<b>Gate</b>');
    expect(tooltip.children).toHaveLength(0);
    expect(f.points[1]['bindTooltip']).not.toHaveBeenCalled();

    m.update({ ...full, car: [13.1, 77.9], heading: null }, false);
    expect(f.api.marker).toHaveBeenCalledTimes(1);
    expect(f.markers[0]['setLatLng']).toHaveBeenCalledWith([13.1, 77.9]);
    expect(f.markers[0]['setIcon']).toHaveBeenCalled();
    expect(f.points[0]['setLatLng']).toHaveBeenCalledWith([12.8, 77.5]);
    expect(f.map.fitBounds).toHaveBeenCalledTimes(1);
  });

  it('removes markers whose points disappear and zooms to a single point', () => {
    const f = fakeLeaflet();
    const m = new LeafletTrackMap(f.api as unknown as LeafletApi, document.createElement('div'));
    m.update(full, false);
    m.update({ ...full, car: null, drop: null }, true);
    expect(f.markers[0]['remove']).toHaveBeenCalled();
    expect(f.points[1]['remove']).toHaveBeenCalled();
    expect(f.map.setView).toHaveBeenLastCalledWith([12.8, 77.5], PUBLIC_TRACK_FOCUS_ZOOM);
    f.map.setView.mockClear();
    m.update({ car: null, heading: null, pickup: null, drop: null, pickupLabel: null, dropLabel: null }, true);
    expect(f.map.setView).not.toHaveBeenCalled();
    m.invalidateSize();
    expect(f.map.invalidateSize).toHaveBeenCalled();
    m.destroy();
    expect(f.map.remove).toHaveBeenCalled();
  });

  it('turns the car icon to the heading', () => {
    expect(carIconHtml(91.6)).toContain('rotate(92deg)');
    expect(carIconHtml(null)).toContain('rotate(0deg)');
  });
});

describe('loadLeaflet / PublicTrackMapFactory', () => {
  it('loads the real Leaflet module on demand and builds the map with it', async () => {
    const L = await loadLeaflet();
    expect(typeof L.map).toBe('function');
    const host = document.createElement('div');
    document.body.appendChild(host);
    const map = await new PublicTrackMapFactory().create(host);
    expect(map).toBeInstanceOf(LeafletTrackMap);
    map.destroy();
    host.remove();
  });
});
