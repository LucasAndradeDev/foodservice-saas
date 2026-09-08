import { maplibreGL } from '@maplibre/maplibre-gl-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { MapPin } from 'lucide-react'
import { setWorkerUrl } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import { useEffect, useRef, useState } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MapContainer, Marker, Tooltip, useMap } from 'react-leaflet'
import { DeliveryRiderIcon } from './DeliveryRiderIcon'

export interface CourierMapDestination {
  id: string
  latitude: number
  longitude: number
  label?: string
}

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

// How long a marker takes to glide from its last reported position to a new one, instead of
// teleporting there the instant a poll (every 4s on the customer page, throttled updates from the
// courier's phone every ~20s) returns a moved coordinate - makes the courier actually look like
// they're moving instead of hopping around, which is the whole point of showing a live map.
const MOVE_ANIMATION_MS = 1000

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

// A flag pin, not the rider icon above - visually distinct at a glance ("where they're headed" vs
// "who's moving"), and never animates/glides since a destination never moves.
const destinationIcon = L.divIcon({
  html: renderToStaticMarkup(
    <div className="flex h-7 w-7 items-center justify-center rounded-full bg-gold-600 text-white shadow-md ring-2 ring-white">
      <MapPin className="h-4 w-4" fill="currentColor" />
    </div>,
  ),
  className: '',
  iconSize: [28, 28],
  iconAnchor: [14, 26],
})

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

// A Marker whose position glides to each new (latitude, longitude) instead of jumping there -
// react-leaflet's own `position` prop just calls setLatLng synchronously on change, so this only
// ever passes it the marker's very first position (captured once via useState's lazy initializer)
// and drives every position after that itself, imperatively, via a requestAnimationFrame loop.
function AnimatedMarker({
  id: _id,
  latitude,
  longitude,
  icon,
  label,
}: CourierMapPosition & { icon: L.DivIcon }) {
  const [initialPosition] = useState<[number, number]>([latitude, longitude])
  const markerRef = useRef<L.Marker>(null)
  const animationFrameRef = useRef<number | undefined>(undefined)

  useEffect(() => {
    const marker = markerRef.current
    if (!marker) return

    const from = marker.getLatLng()
    const to = L.latLng(latitude, longitude)
    if (from.equals(to)) return

    if (animationFrameRef.current) cancelAnimationFrame(animationFrameRef.current)
    const start = performance.now()

    function step(now: number) {
      const progress = Math.min((now - start) / MOVE_ANIMATION_MS, 1)
      marker!.setLatLng([from.lat + (to.lat - from.lat) * progress, from.lng + (to.lng - from.lng) * progress])
      if (progress < 1) {
        animationFrameRef.current = requestAnimationFrame(step)
      }
    }
    animationFrameRef.current = requestAnimationFrame(step)

    return () => {
      if (animationFrameRef.current) cancelAnimationFrame(animationFrameRef.current)
    }
  }, [latitude, longitude])

  return (
    <Marker ref={markerRef} position={initialPosition} icon={icon}>
      {label && (
        <Tooltip permanent direction="top" offset={[0, -16]}>
          {label}
        </Tooltip>
      )}
    </Marker>
  )
}

// Recenters/refits the map whenever the set of positions (or the focused courier) changes - a
// focused courier gets centered close up, otherwise a single marker gets centered at a fixed zoom
// and multiple markers get fitBounds so every one of them stays visible. Follow-up pans (once the
// map already has a view) glide instead of jumping, same reasoning as AnimatedMarker above - only
// the very first view of a session is instant, via MapContainer's own initial `center`.
function MapAutoView({
  positions,
  destinations,
  focusedId,
}: {
  positions: CourierMapPosition[]
  destinations: CourierMapDestination[]
  focusedId?: string | null
}) {
  const map = useMap()

  useEffect(() => {
    if (positions.length === 0 && destinations.length === 0) return

    // An explicit focus always wins and only ever targets a courier - picking one flies in close
    // regardless of how many destination pins are also on the map.
    const focused = focusedId ? positions.find((p) => p.id === focusedId) : undefined
    if (focused) {
      map.flyTo([focused.latitude, focused.longitude], 16)
      return
    }

    const points = [...positions, ...destinations]
    if (points.length === 1) {
      // flyTo, not panTo - panTo only moves the center and leaves zoom wherever fitBounds last
      // left it (e.g. zoomed out to fit several couriers a moment ago, before the rest went
      // offline/unavailable and this became the only marker left) - flyTo animates both.
      map.flyTo([points[0].latitude, points[0].longitude], 15)
      return
    }
    const bounds = L.latLngBounds(points.map((p) => [p.latitude, p.longitude] as [number, number]))
    map.fitBounds(bounds, { padding: [32, 32] })
    // positions/destinations are fresh arrays/objects each poll - comparing serialized coordinates
    // avoids re-fitting (and fighting the user's own pan/zoom) on every 15s refetch when nothing moved.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    focusedId,
    JSON.stringify(positions.map((p) => [p.id, p.latitude, p.longitude])),
    JSON.stringify(destinations.map((d) => [d.id, d.latitude, d.longitude])),
  ])

  return null
}

export function CourierMap({
  positions,
  destinations = [],
  focusedId,
}: {
  positions: CourierMapPosition[]
  // Static pins for where a delivery is headed - never animated/gliding, unlike the courier
  // markers above, since a destination never moves.
  destinations?: CourierMapDestination[]
  // Selecting a courier elsewhere (e.g. DeliveryPage's dropdown) flies the map to them, overriding
  // the default fit-everyone-in-view behavior until cleared.
  focusedId?: string | null
}) {
  const first = positions[0] ?? destinations[0]
  if (!first) return null

  return (
    <MapContainer center={[first.latitude, first.longitude]} zoom={15} scrollWheelZoom className="h-64 w-full rounded-xl">
      <VectorBaseLayer />
      <MapAutoView positions={positions} destinations={destinations} focusedId={focusedId} />
      {positions.map((position) => (
        <AnimatedMarker key={position.id} {...position} icon={position.available === false ? busyCourierIcon : courierIcon} />
      ))}
      {destinations.map((destination) => (
        <Marker key={destination.id} position={[destination.latitude, destination.longitude]} icon={destinationIcon}>
          {destination.label && (
            <Tooltip permanent direction="top" offset={[0, -22]}>
              {destination.label}
            </Tooltip>
          )}
        </Marker>
      ))}
    </MapContainer>
  )
}
