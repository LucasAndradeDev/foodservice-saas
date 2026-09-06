import { maplibreGL } from '@maplibre/maplibre-gl-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { setWorkerUrl } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import { useEffect } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MapContainer, Marker, Tooltip, useMap } from 'react-leaflet'
import { DeliveryRiderIcon } from './DeliveryRiderIcon'

setWorkerUrl(maplibreWorkerUrl)

export interface CourierMapPosition {
  id: string
  latitude: number
  longitude: number
  label?: string
  // Omitted (public tracking page, single-courier views) draws the normal on-brand pin. Only the
  // staff dispatch map ever sets this to false, to grey out couriers currently on a delivery.
  available?: boolean
}

// OpenFreeMap's "Liberty" style - free vector tiles, no API key, no request limit, and (unlike
// CartoDB/Esri's free tiers) explicitly fine for a commercial product. Vector, not raster, so it
// renders through MapLibre GL rather than a plain Leaflet <TileLayer> - bridged onto the same
// Leaflet map instance via @maplibre/maplibre-gl-leaflet, so markers/tooltips below are untouched.
const MAP_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty'

// A colored circle behind the existing rider icon, not Leaflet's default marker image - sidesteps
// the well-known "default Leaflet marker icon 404s under a bundler" issue entirely, and stays
// on-brand with the rest of the delivery UI. className: '' drops leaflet-div-icon's own default
// white-box styling, which would otherwise show through behind ours.
function buildCourierIcon(colorClassName: string) {
  return L.divIcon({
    html: renderToStaticMarkup(
      <div className={`flex h-8 w-8 items-center justify-center rounded-full ${colorClassName} text-white shadow-md ring-2 ring-white`}>
        <DeliveryRiderIcon className="h-5 w-5" />
      </div>,
    ),
    className: '',
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  })
}

const courierIcon = buildCourierIcon('bg-brand-600')
// Grey rather than hidden - still worth showing where a busy courier is (e.g. on the way back
// past a new order's neighborhood), just visually secondary to who's actually free.
const busyCourierIcon = buildCourierIcon('bg-gray-400')

// Adds the MapLibre GL vector basemap to the underlying Leaflet map once, on mount - there's only
// ever one style, so nothing here needs to react to prop changes.
const ATTRIBUTION = '&copy; OpenFreeMap &copy; OpenMapTiles &copy; OpenStreetMap contributors'

function VectorBaseLayer() {
  const map = useMap()

  useEffect(() => {
    const gl = maplibreGL({ style: MAP_STYLE_URL }).addTo(map)
    map.attributionControl.addAttribution(ATTRIBUTION)
    return () => {
      map.removeLayer(gl)
      map.attributionControl.removeAttribution(ATTRIBUTION)
    }
  }, [map])

  return null
}

// Recenters/refits the map whenever the set of positions (or the focused courier) changes - a
// focused courier gets centered close up, otherwise a single marker gets centered at a fixed zoom
// and multiple markers get fitBounds so every one of them stays visible.
function MapAutoView({ positions, focusedId }: { positions: CourierMapPosition[]; focusedId?: string | null }) {
  const map = useMap()

  useEffect(() => {
    if (positions.length === 0) return

    const focused = focusedId ? positions.find((p) => p.id === focusedId) : undefined
    if (focused) {
      map.flyTo([focused.latitude, focused.longitude], 16)
      return
    }

    if (positions.length === 1) {
      map.setView([positions[0].latitude, positions[0].longitude], 15)
      return
    }
    const bounds = L.latLngBounds(positions.map((p) => [p.latitude, p.longitude] as [number, number]))
    map.fitBounds(bounds, { padding: [32, 32] })
    // positions is a fresh array/objects each poll - comparing its serialized coordinates avoids
    // re-fitting (and fighting the user's own pan/zoom) on every 15s refetch when nothing moved.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusedId, JSON.stringify(positions.map((p) => [p.id, p.latitude, p.longitude]))])

  return null
}

export function CourierMap({
  positions,
  focusedId,
}: {
  positions: CourierMapPosition[]
  // Selecting a courier elsewhere (e.g. DeliveryPage's dropdown) flies the map to them, overriding
  // the default fit-everyone-in-view behavior until cleared.
  focusedId?: string | null
}) {
  if (positions.length === 0) return null

  return (
    <MapContainer center={[positions[0].latitude, positions[0].longitude]} zoom={15} scrollWheelZoom className="h-64 w-full rounded-xl">
      <VectorBaseLayer />
      <MapAutoView positions={positions} focusedId={focusedId} />
      {positions.map((position) => (
        <Marker
          key={position.id}
          position={[position.latitude, position.longitude]}
          icon={position.available === false ? busyCourierIcon : courierIcon}
        >
          {position.label && <Tooltip permanent direction="top" offset={[0, -16]}>{position.label}</Tooltip>}
        </Marker>
      ))}
    </MapContainer>
  )
}
