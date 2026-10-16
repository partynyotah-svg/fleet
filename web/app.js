const API_BASE = window.location.origin;
const apiUrl = (path) => path.startsWith('http') ? path : `${API_BASE}${path}`;

const state = {
  operator: null,
  csrf: '',
  csrfHeader: 'X-CSRF-TOKEN',
  vehicles: [],
  trips: [],
  alerts: [],
  geofences: [],
  geofenceEvents: [],
  payments: [],
  audit: [],
  config: { googleMapsApiKey: '', commandsEnabled: false, messagingEnabled: false, trackerSmsActivationEnabled: false, trackerSmsProvider: 'Not configured', trackerActivationServer: '' },
  view: 'overview',
  search: '',
  status: '',
  selectedVehicle: null,
  map: null,
  mapProvider: '',
  layer: 'street',
  markerMap: new Map(),
  geofenceLayers: new Map(),
  tileLayers: {},
  streamAbort: null,
};

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
})[char]);
const toDate = (value) => value ? new Date(value) : null;
const dateTime = (value) => {
  const date = toDate(value);
  return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date) : 'No reading received';
};
const fmtUtc = (value) => {
  const date = toDate(value);
  return date && !Number.isNaN(date.getTime()) ? `${date.toISOString().slice(0, 16).replace('T', ' ')}Z` : '—';
};
const currentVehicle = (id) => state.vehicles.find((vehicle) => vehicle.id === id);

async function getCsrf() {
  const response = await fetch(apiUrl('/api/v1/auth/csrf'), { credentials: 'include' });
  if (!response.ok) throw new Error('Could not initialize a secure session.');
  const data = await response.json();
  state.csrf = data.token;
  state.csrfHeader = data.headerName;
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (!['GET', 'HEAD', 'OPTIONS'].includes((options.method || 'GET').toUpperCase()) && state.csrf) headers.set(state.csrfHeader, state.csrf);
  const response = await fetch(apiUrl(path), { ...options, headers, credentials: 'include' });
  if (response.status === 401) {
    signOut(false);
    throw new Error('Your operator session has ended. Sign in again.');
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      message = body.error || body.detail || body.message || message;
    } catch { /* The security entry point may intentionally return an empty body. */ }
    throw new Error(message);
  }
  if (response.status === 204) return null;
  if (response.headers.get('content-type')?.includes('application/json')) return response.json();
  return response;
}

function toast(message, kind = '') {
  const node = document.createElement('div');
  node.className = `toast${kind ? ` ${kind}` : ''}`;
  node.textContent = message;
  $('#toast-region').append(node);
  window.setTimeout(() => node.remove(), 4000);
}

async function login(event) {
  event.preventDefault();
  const fields = new FormData(event.currentTarget);
  const button = $('#login-form button[type=submit]');
  button.disabled = true;
  button.textContent = 'Verifying credentials…';
  $('#login-error').textContent = '';
  try {
    await getCsrf();
    state.operator = await api('/api/v1/auth/login', {
      method: 'POST', body: JSON.stringify({ email: fields.get('email'), password: fields.get('password') }),
    });
    await getCsrf();
    await openWorkspace();
  } catch (error) {
    $('#login-error').textContent = error.message;
  } finally {
    button.disabled = false;
    button.innerHTML = 'Sign in securely <span>↗</span>';
  }
}

async function openWorkspace() {
  $('#login-screen').classList.add('hidden');
  $('#app').classList.remove('hidden');
  $('#user-avatar').textContent = initials(state.operator?.email || 'OP');
  $('#user-avatar').title = `${state.operator?.email || ''} · ${state.operator?.role || ''}`;
  try {
    [state.vehicles, state.trips, state.alerts, state.config, state.geofences, state.geofenceEvents, state.payments] = await Promise.all([
      api('/api/v1/fleet'), api('/api/v1/trips'), api('/api/v1/alerts'), api('/api/v1/config'),
      api('/api/v1/geofences'), api('/api/v1/geofences/events'), api('/api/v1/billing/payments'),
    ]);
    render();
    renderGeofences();
    renderPayments();
    await initializeMap();
    connectLiveFeed();
    if (state.operator?.role === 'ADMIN') loadAudit();
  } catch (error) {
    toast(error.message, 'error');
  }
}

async function signOut(remote = true) {
  state.streamAbort?.abort();
  state.streamAbort = null;
  const previous = state.operator;
  if (remote && previous) {
    try {
      await fetch(apiUrl('/api/v1/auth/logout'), { method: 'POST', credentials: 'include', headers: { [state.csrfHeader]: state.csrf } });
    } catch { /* Clear local state even if the server is unreachable. */ }
  }
  state.operator = null;
  state.csrf = '';
  $('#app').classList.add('hidden');
  $('#login-screen').classList.remove('hidden');
}

function initials(value) { return String(value).split('@')[0].split(/[._ -]/).filter(Boolean).slice(0, 2).map((part) => part[0].toUpperCase()).join('') || 'OP'; }

function render() {
  renderKpis();
  renderQueue();
  renderFleet();
  renderTrips();
  renderExceptions();
  renderMapMarkers();
  updateFeedState(true);
  $('#exception-badge').textContent = state.alerts.length;
  $('#exception-badge').classList.toggle('hidden', state.alerts.length === 0);
  $('#map-count').textContent = `${state.vehicles.filter((vehicle) => vehicle.latitude != null && vehicle.longitude != null).length} VEHICLES SHOWN`;
  $('#map-empty').classList.toggle('hidden', state.vehicles.some((vehicle) => vehicle.latitude != null && vehicle.longitude != null));
}

function renderKpis() {
  const now = Date.now();
  const fresh = state.vehicles.map((vehicle) => toDate(vehicle.lastSeenAt)?.getTime()).filter(Number.isFinite);
  const mostRecent = fresh.length ? Math.max(...fresh) : null;
  $('#kpi-tracked').textContent = state.vehicles.length;
  $('#kpi-moving').textContent = state.vehicles.filter((vehicle) => vehicle.status === 'ON_TRIP').length;
  $('#kpi-available').textContent = state.vehicles.filter((vehicle) => vehicle.status === 'AVAILABLE').length;
  $('#kpi-exceptions').textContent = state.alerts.length;
  $('#kpi-freshness').textContent = mostRecent == null ? 'No signal' : `${Math.max(0, Math.floor((now - mostRecent) / 60000))}m ago`;
}

function renderQueue() {
  const visible = state.alerts.slice(0, 8);
  $('#queue-list').innerHTML = visible.length ? visible.map((alert) => `
    <article class="exception-item ${alert.severity === 'CRITICAL' ? '' : 'warning'}"><span class="exception-mark">!</span><div><strong>${esc(alert.title)}</strong><p>${esc(alert.vehicleCode)} · ${esc(alert.detail)}</p><button data-focus-vehicle="${esc(alert.vehicleId)}">Inspect record ↗</button></div></article>`).join('') : '<div class="empty-note">Nothing needs attention. Exceptions appear only when device data crosses a threshold.</div>';
}

function renderFleet() {
  const needle = state.search.trim().toLowerCase();
  const rows = state.vehicles.filter((vehicle) => {
    const text = [vehicle.vehicleCode, vehicle.make, vehicle.model, vehicle.driverName].join(' ').toLowerCase();
    return (!needle || text.includes(needle)) && (!state.status || vehicle.status === state.status);
  });
  $('#fleet-count').textContent = `${rows.length} record${rows.length === 1 ? '' : 's'}`;
  const admin = state.operator?.role === 'ADMIN';
  $('#fleet-rows').innerHTML = rows.length ? rows.map((vehicle) => {
    const lastSeen = toDate(vehicle.lastSeenAt);
    const stale = !lastSeen || Date.now() - lastSeen.getTime() > 5 * 60_000;
    return `<tr>
      <td><div class="vehicle-cell"><span class="vehicle-symbol">▰</span><span><strong>${esc(vehicle.vehicleCode)}</strong><small>${esc(vehicle.make)} ${esc(vehicle.model)}${vehicle.modelYear ? ` · ${esc(vehicle.modelYear)}` : ''}</small></span></div></td>
      <td>${esc(vehicle.driverName)}${vehicle.driverPhone ? `<small>${esc(vehicle.driverPhone)}</small>` : ''}</td>
      <td><span class="status-pill ${esc(vehicle.status.toLowerCase().replace('_','-'))}">${esc(statusLabel(vehicle.status))}</span></td>
      <td>${vehicle.fuelPercent == null ? '—' : `<span class="fuel-track"><i class="${vehicle.fuelPercent < 20 ? 'low' : ''}" style="width:${Math.max(0,Math.min(100,vehicle.fuelPercent))}%"></i></span>${vehicle.fuelPercent}%`}</td>
      <td><div class="signal-cell"><strong>${stale ? 'STALE / NONE' : 'RECEIVING'}</strong><small>${esc(dateTime(vehicle.lastSeenAt))}</small></div></td>
      <td>${vehicle.latitude == null ? '—' : `${Number(vehicle.latitude).toFixed(5)}, ${Number(vehicle.longitude).toFixed(5)}`}</td>
      <td><div class="row-actions"><button data-focus-vehicle="${esc(vehicle.id)}">Map</button><button data-message-vehicle="${esc(vehicle.id)}">Message</button>${admin ? `<button data-activate-vehicle="${esc(vehicle.id)}" ${vehicle.trackerProtocol !== 'TK103' || !vehicle.trackerPhone || !state.config.trackerSmsActivationEnabled ? 'disabled title="Configure a TK103 tracker, SIM phone and SMS provider first"' : ''}>Activate SMS</button>` : ''}${['ADMIN','DISPATCHER'].includes(state.operator?.role) ? `<button data-edit-vehicle="${esc(vehicle.id)}">Edit</button>` : ''}${admin ? `<button class="delete" data-delete-vehicle="${esc(vehicle.id)}">Remove</button>` : ''}</div></td>
    </tr>`;
  }).join('') : `<tr><td colspan="7" class="empty-note">${state.vehicles.length ? 'No records match these filters.' : 'No vehicles enrolled. Enroll a vehicle and pair its tracking device to begin.'}</td></tr>`;
}

function renderTrips() {
  const planned = state.trips.filter((trip) => trip.status === 'PLANNED').length;
  $('#trip-planned-count').textContent = planned;
  $('#trip-active-count').textContent = state.trips.filter((trip) => trip.status === 'ACTIVE').length;
  $('#trip-completed-count').textContent = state.trips.filter((trip) => trip.status === 'COMPLETED').length;
  $('#trip-list').innerHTML = state.trips.length ? state.trips.map((trip) => `
    <article class="trip-row"><div><strong>${esc(trip.tripCode)}</strong><small>${esc(fmtUtc(trip.plannedStart))}</small></div><div><strong>${esc(trip.vehicleCode)}</strong><small>${esc(trip.driverName)}</small></div><div class="trip-route">${esc(trip.origin)} <span>→</span> ${esc(trip.destination)}</div><span class="status-pill ${esc(trip.status.toLowerCase())}">${esc(trip.status)}</span>${trip.status === 'PLANNED' && ['ADMIN','DISPATCHER'].includes(state.operator?.role) ? `<button class="row-actions trip-cancel" data-cancel-trip="${esc(trip.id)}">Cancel</button>` : trip.status === 'ACTIVE' && ['ADMIN','DISPATCHER'].includes(state.operator?.role) ? `<button class="row-actions trip-complete" data-complete-trip="${esc(trip.id)}">Complete</button>` : ''}</article>`).join('') : '<div class="empty-note">No trips recorded. Planning a trip requires an enrolled available vehicle.</div>';
  const upcoming = state.trips.filter((trip) => trip.status === 'PLANNED' || trip.status === 'ACTIVE').slice(0, 3);
  $('#next-trips').innerHTML = upcoming.length ? upcoming.map((trip) => `<article class="trip-tile"><span class="trip-glyph">↗</span><span><strong>${esc(trip.destination)}</strong><small>${esc(trip.vehicleCode)} · ${esc(dateTime(trip.plannedStart))}</small></span><span class="trip-state">${esc(trip.status)}</span></article>`).join('') : '<div class="empty-note">No trips planned. Plan a trip when a vehicle is available.</div>';
}

function renderExceptions() {
  $('#exception-list').innerHTML = state.alerts.length ? state.alerts.map((alert) => `
    <article class="exception-card"><span class="exception-mark">!</span><div><strong>${esc(alert.title)} · ${esc(alert.vehicleCode)}</strong><p>${esc(alert.detail)} · ${esc(dateTime(alert.createdAt))}</p></div><span class="status-pill ${alert.severity === 'CRITICAL' ? 'maintenance' : ''}">${esc(alert.severity)}</span><button class="text-button" data-focus-vehicle="${esc(alert.vehicleId)}">Open vehicle ↗</button></article>`).join('') : '<div class="empty-note">No open exceptions. Low-fuel and stale-signal alerts will appear here when confirmed by received telemetry.</div>';
}

async function loadAudit() {
  try {
    state.audit = await api('/api/v1/audit');
    $('#activity-rows').innerHTML = state.audit.length ? state.audit.map((item) => `
      <tr><td>${esc(fmtUtc(item.createdAt))}</td><td>${esc(item.actorEmail)}</td><td>${esc(item.action)}</td><td>${esc(item.objectType)}${item.objectId ? ` · ${esc(item.objectId.slice(0,8))}` : ''}</td><td>${esc(item.detail)}</td></tr>`).join('') : '<tr><td colspan="5" class="empty-note">No operator changes recorded.</td></tr>';
  } catch (error) { toast(error.message, 'error'); }
}

function statusLabel(status) { return ({ ON_TRIP: 'On trip', AVAILABLE: 'Available', MAINTENANCE: 'Maintenance', RETIRED: 'Retired' })[status] || status; }

function updateFeedState(live, failed = false) {
  const node = $('#feed-status');
  node.classList.toggle('live', live && !failed);
  node.classList.toggle('error', failed);
  $('span', node).textContent = failed ? 'FEED RECONNECTING' : live ? 'DEVICE FEED' : 'CONNECTING';
}

function connectLiveFeed() {
  state.streamAbort?.abort();
  const controller = new AbortController();
  state.streamAbort = controller;
  fetch('/api/v1/fleet/stream', { credentials: 'same-origin', signal: controller.signal, headers: { Accept: 'text/event-stream' } }).then(async (response) => {
    if (response.status === 401) { signOut(false); throw new Error('Session expired'); }
    if (!response.ok || !response.body) throw new Error('Feed unavailable');
    updateFeedState(true);
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split('\n\n');
      buffer = events.pop() || '';
      for (const event of events) {
        const data = event.split('\n').find((line) => line.startsWith('data:'));
        if (!data) continue;
        try {
          state.vehicles = JSON.parse(data.slice(5).trim());
          render();
        } catch { /* Ignore a malformed event and keep listening. */ }
      }
    }
    if (state.streamAbort === controller) reconnectFeed();
  }).catch(() => {
    if (state.streamAbort === controller && state.operator) {
      updateFeedState(false, true);
      reconnectFeed();
    }
  });
}

function reconnectFeed() {
  if (!state.operator || document.hidden) return;
  window.setTimeout(() => { if (state.operator) connectLiveFeed(); }, 3000);
}

async function initializeMap() {
  if (state.map) return;
  if (state.config.googleMapsApiKey) {
    try {
      await loadGoogleMaps(state.config.googleMapsApiKey);
      state.map = new google.maps.Map($('#map'), { center: { lat: 15, lng: 0 }, zoom: 2, mapTypeControl: false, streetViewControl: false, fullscreenControl: false, clickableIcons: false });
      state.mapProvider = 'google';
      $('#map-provider').textContent = 'GOOGLE MAPS';
      $('#map-credit').classList.add('hidden');
      renderMapMarkers();
      renderGeofenceLayers();
      fitMap();
      return;
    } catch { toast('Google Maps unavailable; using OpenStreetMap.'); }
  }
  if (!window.L) { $('#map-provider').textContent = 'MAP UNAVAILABLE'; return; }
  state.map = L.map('map', { zoomControl: true }).setView([15, 0], 2);
  state.mapProvider = 'leaflet';
  state.tileLayers.street = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a>' });
  state.tileLayers.satellite = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', { maxZoom: 19, attribution: 'Tiles &copy; Esri' });
  state.tileLayers.street.addTo(state.map);
  renderMapMarkers();
  renderGeofenceLayers();
  fitMap();
}

function renderGeofenceLayers() {
  if (!state.map) return;
  const active = state.geofences.filter((zone) => zone.enabled);
  const activeIds = new Set(active.map((zone) => zone.id));
  for (const zone of active) {
    const center = { lat: Number(zone.latitude), lng: Number(zone.longitude) };
    let layer = state.geofenceLayers.get(zone.id);
    if (state.mapProvider === 'leaflet') {
      if (!layer) {
        layer = L.circle([center.lat, center.lng], { radius: Number(zone.radiusMeters), color: '#ad8132', weight: 2, fillColor: '#ad8132', fillOpacity: 0.08 }).addTo(state.map);
        state.geofenceLayers.set(zone.id, layer);
      } else {
        layer.setLatLng([center.lat, center.lng]);
        layer.setRadius(Number(zone.radiusMeters));
      }
    } else if (state.mapProvider === 'google') {
      if (!layer) {
        layer = new google.maps.Circle({ map: state.map, center, radius: Number(zone.radiusMeters), strokeColor: '#ad8132', strokeOpacity: 0.85, strokeWeight: 2, fillColor: '#ad8132', fillOpacity: 0.08 });
        state.geofenceLayers.set(zone.id, layer);
      } else {
        layer.setCenter(center);
        layer.setRadius(Number(zone.radiusMeters));
        layer.setMap(state.map);
      }
    }
  }
  for (const [id, layer] of state.geofenceLayers) {
    if (activeIds.has(id)) continue;
    if (state.mapProvider === 'leaflet') state.map.removeLayer(layer);
    else layer.setMap(null);
    state.geofenceLayers.delete(id);
  }
}

function loadGoogleMaps(key) {
  return new Promise((resolve, reject) => {
    const callback = `relayMaps${Date.now()}`;
    const timeout = setTimeout(() => reject(new Error('Google Maps timed out')), 9000);
    window[callback] = () => { clearTimeout(timeout); delete window[callback]; resolve(); };
    const script = document.createElement('script');
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}&callback=${callback}&v=weekly`;
    script.async = true;
    script.onerror = () => { clearTimeout(timeout); delete window[callback]; reject(new Error('Google Maps failed to load')); };
    document.head.append(script);
  });
}

function markerClass(vehicle) {
  const stale = !vehicle.lastSeenAt || Date.now() - new Date(vehicle.lastSeenAt).getTime() > 300000;
  if (vehicle.status === 'MAINTENANCE' || (vehicle.fuelPercent != null && vehicle.fuelPercent < 20)) return 'alert';
  if (stale || vehicle.status !== 'ON_TRIP') return 'idle';
  return '';
}

function leafletCar(vehicle) {
  const stateClass = markerClass(vehicle);
  return L.divIcon({ className: 'vehicle-marker', html: `<div class="tiny-car ${stateClass}" title="${esc(vehicle.vehicleCode)}"><i></i><em></em><b></b><span class="wheel left"></span><span class="wheel right"></span></div>`, iconSize: [30, 21], iconAnchor: [15, 10] });
}

function renderMapMarkers() {
  if (!state.map) return;
  const hasLocation = new Set(state.vehicles.filter((vehicle) => vehicle.latitude != null && vehicle.longitude != null).map((vehicle) => vehicle.id));
  for (const vehicle of state.vehicles) {
    if (vehicle.latitude == null || vehicle.longitude == null) continue;
    let marker = state.markerMap.get(vehicle.id);
    const lat = Number(vehicle.latitude), lng = Number(vehicle.longitude);
    if (state.mapProvider === 'leaflet') {
      if (!marker) {
        marker = L.marker([lat, lng], { icon: leafletCar(vehicle), title: `${vehicle.vehicleCode} · ${vehicle.make} ${vehicle.model}` }).addTo(state.map);
        marker.on('click', () => focusVehicle(vehicle.id));
        state.markerMap.set(vehicle.id, marker);
      } else {
        marker.setLatLng([lat, lng]);
        marker.setIcon(leafletCar(vehicle));
      }
    } else if (state.mapProvider === 'google') {
      const color = markerClass(vehicle) === 'alert' ? '#c94f35' : markerClass(vehicle) === 'idle' ? '#859188' : '#3b7c60';
      if (!marker) {
        marker = new google.maps.Marker({ position: { lat, lng }, title: `${vehicle.vehicleCode} · ${vehicle.make} ${vehicle.model}`, map: state.map, label: { text: '▪', color: '#fff', fontSize: '11px' }, icon: { path: 'M-7,-4 L-4,-4 L-2,-7 L3,-7 L6,-4 L8,-3 L8,4 L6,4 L6,6 L3,6 L3,4 L-3,4 L-3,6 L-6,6 L-6,4 L-8,4 L-8,-2 Z', fillColor: color, fillOpacity: 1, strokeColor: '#ffffff', strokeWeight: 1.4, scale: 1 } });
        marker.addListener('click', () => focusVehicle(vehicle.id));
        state.markerMap.set(vehicle.id, marker);
      } else {
        marker.setPosition({ lat, lng });
        marker.setIcon({ ...marker.getIcon(), fillColor: color });
      }
    }
  }
  for (const [id, marker] of state.markerMap) {
    if (!hasLocation.has(id)) {
      if (state.mapProvider === 'leaflet') marker.remove(); else marker.setMap(null);
      state.markerMap.delete(id);
    }
  }
}

function fitMap() {
  if (!state.map) return;
  const points = state.vehicles.filter((vehicle) => vehicle.latitude != null && vehicle.longitude != null);
  if (!points.length) return;
  if (state.mapProvider === 'leaflet') state.map.fitBounds(L.latLngBounds(points.map((vehicle) => [Number(vehicle.latitude), Number(vehicle.longitude)])).pad(.14), { maxZoom: 14 });
  else {
    const bounds = new google.maps.LatLngBounds();
    points.forEach((vehicle) => bounds.extend({ lat: Number(vehicle.latitude), lng: Number(vehicle.longitude) }));
    state.map.fitBounds(bounds, 40);
  }
}

function switchLayer() {
  if (!state.map) return;
  state.layer = state.layer === 'street' ? 'satellite' : 'street';
  $('#map-layer').innerHTML = `${state.layer.toUpperCase()} <span>⌄</span>`;
  if (state.mapProvider === 'leaflet') {
    for (const layer of Object.values(state.tileLayers)) state.map.removeLayer(layer);
    state.tileLayers[state.layer].addTo(state.map);
  } else state.map.setMapTypeId(state.layer === 'street' ? 'roadmap' : 'satellite');
}

function navigate(view) {
  state.view = view;
  for (const section of $$('.view')) section.classList.toggle('hidden', section.id !== `view-${view}`);
  for (const button of $$('.rail-link[data-view]')) button.classList.toggle('active', button.dataset.view === view);
  $('#crumb').textContent = ({ overview: 'COMMAND CENTER', fleet: 'FLEET REGISTER', trips: 'TRIP BOARD', geofences: 'GEOFENCES', billing: 'BILLING LEDGER', exceptions: 'REVIEW QUEUE', activity: 'ACTIVITY LOG' })[view] || 'COMMAND CENTER';
  if (view === 'activity' && state.operator?.role === 'ADMIN') loadAudit();
  if (view === 'overview' && state.map) setTimeout(() => { state.map.invalidateSize?.(); fitMap(); }, 50);
}

function modal(title, subtitle, content, onSubmit) {
  const root = $('#modal-root');
  root.innerHTML = `<div class="modal-backdrop"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><header class="modal-head"><div><h2 id="modal-title">${esc(title)}</h2><p>${esc(subtitle)}</p></div><button class="modal-close" type="button" data-close>×</button></header>${content}</section></div>`;
  const backdrop = $('.modal-backdrop', root);
  backdrop.addEventListener('click', (event) => { if (event.target === backdrop) closeModal(); });
  $$('[data-close]', root).forEach((button) => button.addEventListener('click', closeModal));
  const form = $('form', root);
  if (form && onSubmit) form.addEventListener('submit', async (event) => { event.preventDefault(); await onSubmit(new FormData(form)); });
  document.addEventListener('keydown', closeOnEscape, { once: true });
}
function closeOnEscape(event) { if (event.key === 'Escape') closeModal(); }
function closeModal() { $('#modal-root').innerHTML = ''; }

function vehicleDialog(vehicle = null) {
  const edit = Boolean(vehicle);
  const v = (key, fallback = '') => esc(vehicle?.[key] ?? fallback);
  const statusOptions = ['AVAILABLE','ON_TRIP','MAINTENANCE','RETIRED'].map((status) => `<option value="${status}" ${v('status','AVAILABLE') === status ? 'selected' : ''}>${esc(statusLabel(status))}</option>`).join('');
  const content = `<form class="modal-form"><div class="modal-grid">
    <div><label>Fleet number<input name="vehicleCode" value="${v('vehicleCode')}" ${edit ? 'readonly' : 'required'} maxlength="32" pattern="[A-Za-z0-9_-]+" placeholder="VH-204"></label></div>
    <div><label>Tracking device ID<input name="deviceId" value="${edit ? '' : ''}" ${edit ? 'disabled' : 'required'} maxlength="120" pattern="[A-Za-z0-9._:-]+" placeholder="device identity"></label></div>
    <div><label>Make<input name="make" value="${v('make')}" required maxlength="80"></label></div>
    <div><label>Model<input name="model" value="${v('model')}" required maxlength="100"></label></div>
    <div><label>Model year<input name="modelYear" type="number" min="1950" max="2150" value="${v('modelYear')}" placeholder="2025"></label></div>
    <div><label>Vehicle status<select name="status">${statusOptions}</select></label></div>
    <div><label>Assigned driver<input name="driverName" value="${v('driverName')}" required maxlength="160"></label></div>
    <div><label>Driver phone (E.164)<input name="driverPhone" type="tel" value="${v('driverPhone')}" maxlength="40" placeholder="+14155550123"></label></div>
    <div><label>Tracker SIM phone (E.164)<input name="trackerPhone" type="tel" value="${v('trackerPhone')}" maxlength="24" pattern="\\+[1-9][0-9]{7,14}" placeholder="+254712345678"></label></div>
    <div><label>Tracker activation protocol<select name="trackerProtocol"><option value="DISABLED" ${v('trackerProtocol','DISABLED') === 'DISABLED' ? 'selected' : ''}>Not configured</option><option value="TK103" ${v('trackerProtocol','DISABLED') === 'TK103' ? 'selected' : ''}>TK103-compatible</option></select></label></div>
  </div>${!edit ? '<p class="modal-info">The device secret is generated once after enrollment. Store it in the tracker securely; it cannot be recovered later. No sample location or telemetry is created.</p>' : ''}<div class="modal-actions"><button type="button" class="button quiet-button" data-close>Cancel</button><button class="button primary-button" type="submit">${edit ? 'Save record' : 'Enroll vehicle'}</button></div></form>`;
  modal(edit ? `Edit ${vehicle.vehicleCode}` : 'Enroll vehicle + device', edit ? 'Change the asset register. Changes are audit logged.' : 'Register a real asset and issue a device credential.', content, async (form) => {
    const data = Object.fromEntries(form.entries());
    data.vehicleCode = data.vehicleCode.trim().toUpperCase();
    data.modelYear = data.modelYear ? Number(data.modelYear) : null;
    if (edit) {
      data.deviceId = vehicle.deviceId || '';
      try {
        const result = await api(`/api/v1/fleet/${encodeURIComponent(vehicle.id)}`, { method: 'PUT', body: JSON.stringify(data) });
        state.vehicles = state.vehicles.map((item) => item.id === result.id ? result : item);
        closeModal(); render(); toast('Vehicle record saved.');
      } catch (error) { toast(error.message, 'error'); }
      return;
    }
    try {
      const result = await api('/api/v1/fleet', { method: 'POST', body: JSON.stringify(data) });
      state.vehicles.unshift(result.vehicle);
      render();
      showProvisionedDevice(result);
    } catch (error) { toast(error.message, 'error'); }
  });
}

function showProvisionedDevice(result) {
  const content = `<div class="modal-copy">Device <strong>${esc(result.deviceId)}</strong> has been paired with <strong>${esc(result.vehicle.vehicleCode)}</strong>. Copy the credential into the device provisioning system now; it will never be shown again.<div class="one-time-token" id="device-token">${esc(result.oneTimeDeviceToken)}</div><div class="modal-actions"><button class="button quiet-button" id="copy-token">Copy token</button><button class="button primary-button" data-close>Done</button></div><p class="modal-warning">Treat this credential like a password. Do not send it over SMS or WhatsApp.</p></div>`;
  modal('Device credential issued', 'This one-time secret is not stored in recoverable form.', content);
  $('#copy-token').addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(result.oneTimeDeviceToken); toast('Device token copied.'); }
    catch { toast('Clipboard unavailable. Select the token and copy it securely.', 'error'); }
  });
}

function deleteVehicleDialog(vehicle) {
  if (state.operator?.role !== 'ADMIN') return toast('Only administrators can remove fleet records.', 'error');
  modal('Retire fleet record?', `${vehicle.vehicleCode} · ${vehicle.make} ${vehicle.model}`, `<div class="modal-copy">This removes the vehicle and its telemetry history. This action is audit logged and cannot be undone. Consider changing the status to Retired instead.<div class="modal-actions"><button class="button quiet-button" data-close>Cancel</button><button class="button primary-button" id="confirm-delete">Remove record</button></div></div>`);
  $('#confirm-delete').addEventListener('click', async () => {
    try { await api(`/api/v1/fleet/${vehicle.id}`, { method: 'DELETE' }); state.vehicles = state.vehicles.filter((item) => item.id !== vehicle.id); state.alerts = state.alerts.filter((item) => item.vehicleId !== vehicle.id); closeModal(); render(); toast('Vehicle removed.'); }
    catch (error) { toast(error.message, 'error'); }
  });
}

function activateTrackerDialog(vehicleId) {
  const vehicle = currentVehicle(vehicleId);
  if (!vehicle || state.operator?.role !== 'ADMIN') return;
  if (!state.config.trackerSmsActivationEnabled || vehicle.trackerProtocol !== 'TK103' || !vehicle.trackerPhone) {
    return toast('Configure an Africa\'s Talking provider, TK103 protocol, and tracker SIM number first.', 'error');
  }
  const content = `<div class="modal-copy"><strong>${esc(vehicle.vehicleCode)} · ${esc(vehicle.deviceId)}</strong><br>Tracker SIM: ${esc(vehicle.trackerPhone)}<br>Provider: ${esc(state.config.trackerSmsProvider)}<br>Server target: ${esc(state.config.trackerActivationServer)}<div class="modal-warning">This sends a real SMS and may incur provider charges. It changes the tracker’s server endpoint. Only continue if this tracker uses the TK103 adminip3 command and the recipient number is its SIM.</div><div class="modal-actions"><button class="button quiet-button" data-close>Cancel</button><button class="button primary-button" id="confirm-tracker-activation">Send activation SMS</button></div></div>`;
  modal('Activate tracking device?', 'A real command will be sent to the tracker SIM.', content);
  $('#confirm-tracker-activation').addEventListener('click', async (event) => {
    const button = event.currentTarget;
    button.disabled = true;
    button.textContent = 'Sending…';
    try {
      const result = await api(`/api/v1/fleet/${encodeURIComponent(vehicle.id)}/activate`, {
        method: 'POST', body: JSON.stringify({ confirm: true }),
      });
      closeModal();
      toast(`${result.message} Recipient ending ${result.recipientSuffix}.`);
    } catch (error) {
      button.disabled = false;
      button.textContent = 'Send activation SMS';
      toast(error.message, 'error');
    }
  });
}

function planTripDialog() {
  const available = state.vehicles.filter((vehicle) => vehicle.status === 'AVAILABLE');
  if (!available.length) return toast('No available vehicles. Verify vehicle status before dispatching.', 'error');
  const earliest = new Date(Date.now() + 5 * 60000); earliest.setMinutes(Math.ceil(earliest.getMinutes() / 5) * 5, 0, 0);
  const content = `<form class="modal-form"><label>Available vehicle<select name="vehicleId" required>${available.map((vehicle) => `<option value="${vehicle.id}">${esc(vehicle.vehicleCode)} · ${esc(vehicle.make)} ${esc(vehicle.model)} · ${esc(vehicle.driverName)}</option>`).join('')}</select></label><div class="modal-grid"><div><label>Origin<input name="origin" maxlength="160" placeholder="Operations depot"></label></div><div><label>Destination<input name="destination" required maxlength="160" placeholder="Customer / site name"></label></div><div class="full-row"><label>Planned departure (local time)<input name="plannedStart" type="datetime-local" required value="${localDateTimeValue(earliest)}"></label></div></div><p class="modal-info">Trip assignment is a dispatch record only. It does not remotely start, immobilize, or steer a vehicle.</p><div class="modal-actions"><button type="button" class="button quiet-button" data-close>Cancel</button><button type="submit" class="button primary-button">Create dispatch</button></div></form>`;
  modal('Plan a trip', 'Assign an available vehicle and planned departure.', content, async (form) => {
    const data = Object.fromEntries(form.entries());
    data.plannedStart = new Date(data.plannedStart).toISOString();
    try { state.trips.unshift(await api('/api/v1/trips', { method: 'POST', body: JSON.stringify(data) })); closeModal(); renderTrips(); render(); toast('Trip added to the dispatch board.'); }
    catch (error) { toast(error.message, 'error'); }
  });
}

function localDateTimeValue(date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

function focusVehicle(id) {
  const vehicle = currentVehicle(id);
  if (!vehicle) return;
  state.selectedVehicle = id;
  navigate('overview');
  const marker = state.markerMap.get(id);
  if (marker && state.mapProvider === 'leaflet') state.map.panTo(marker.getLatLng());
  if (marker && state.mapProvider === 'google') state.map.panTo(marker.getPosition());
  if (vehicle.latitude == null) toast(`${vehicle.vehicleCode} has no verified location yet.`);
  else showHistory(vehicle);
}

async function showHistory(vehicle) {
  let points = [];
  try { points = await api(`/api/v1/fleet/${vehicle.id}/history?after=${encodeURIComponent(new Date(Date.now() - 86400000).toISOString())}`); }
  catch { /* Keep the current position available if history is unavailable. */ }
  const latest = points[0];
  modal(`${vehicle.vehicleCode} · position history`, `${vehicle.make} ${vehicle.model} · ${vehicle.driverName}`, `<div class="modal-copy"><strong>Last device reading</strong><br>${esc(dateTime(vehicle.lastSeenAt))}<br><br><strong>Coordinates</strong><br>${Number(vehicle.latitude).toFixed(6)}, ${Number(vehicle.longitude).toFixed(6)}${latest ? `<br><br><strong>History points in previous 24 hours</strong><br>${points.length} (newest: ${esc(dateTime(latest.capturedAt))})` : '<br><br>No history points in the previous 24 hours.'}<div class="modal-actions"><button class="button quiet-button" data-close>Close</button></div></div>`);
}

async function exportCsv() {
  try {
    const response = await api('/api/v1/reports/fleet.csv');
    const url = URL.createObjectURL(await response.blob());
    const anchor = document.createElement('a');
    anchor.href = url; anchor.download = 'relay-fleet.csv'; anchor.click();
    URL.revokeObjectURL(url);
    toast('Current fleet report downloaded.');
  } catch (error) { toast(error.message, 'error'); }
}

async function exportReport() {
  const format = $('#report-format').value;
  const endpoints = { csv: 'fleet.csv', pdf: 'fleet.pdf', xlsx: 'fleet.xlsx', docx: 'fleet.docx' };
  const response = await api(`/api/v1/reports/${endpoints[format]}`);
  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `relay-fleet.${format}`;
  anchor.click();
  URL.revokeObjectURL(url);
  toast(`${format.toUpperCase()} report downloaded.`);
}

function renderGeofences() {
  const admin = state.operator?.role === 'ADMIN';
  $('#geofence-create').classList.toggle('hidden', !['ADMIN', 'DISPATCHER'].includes(state.operator?.role));
  $('#geofence-rows').innerHTML = state.geofences.length ? state.geofences.map((zone) => `<tr>
    <td><strong>${esc(zone.name)}</strong></td><td>${Number(zone.latitude).toFixed(5)}, ${Number(zone.longitude).toFixed(5)}</td>
    <td>${Number(zone.radiusMeters).toLocaleString()} m</td><td><span class="status-pill ${zone.enabled ? 'on-trip' : 'retired'}">${zone.enabled ? 'ACTIVE' : 'ARCHIVED'}</span></td>
    <td>${esc(zone.createdBy)}</td><td>${admin && zone.enabled ? `<button class="button quiet-button" data-archive-geofence="${esc(zone.id)}">Archive</button>` : ''}</td>
  </tr>`).join('') : '<tr><td colspan="6">No geofences configured.</td></tr>';
  $('#geofence-event-rows').innerHTML = state.geofenceEvents.length ? state.geofenceEvents.map((event) => `<tr>
    <td>${esc(dateTime(event.capturedAt))}</td><td>${esc(event.vehicleCode)}</td><td>${esc(event.geofenceName)}</td><td>${esc(event.type)}</td>
  </tr>`).join('') : '<tr><td colspan="4">No boundary transitions received.</td></tr>';
  renderGeofenceLayers();
}

function geofenceDialog() {
  const content = `<form class="modal-form"><label>Zone name<input name="name" required maxlength="100" placeholder="Depot perimeter"></label><div class="modal-grid"><label>Center latitude<input name="latitude" type="number" step="any" min="-90" max="90" required></label><label>Center longitude<input name="longitude" type="number" step="any" min="-180" max="180" required></label><label>Radius in meters<input name="radiusMeters" type="number" min="10" max="50000" value="500" required></label></div><p class="modal-info">Use coordinates from a verified location. The zone is circular and transitions are evaluated from device readings; no position is generated.</p><div class="modal-actions"><button type="button" class="button quiet-button" data-close>Cancel</button><button type="submit" class="button primary-button">Create geofence</button></div></form>`;
  modal('Create geofence', 'Define a circular monitoring zone.', content, async (form) => {
    const data = Object.fromEntries(form.entries());
    data.latitude = Number(data.latitude);
    data.longitude = Number(data.longitude);
    data.radiusMeters = Number(data.radiusMeters);
    try {
      const zone = await api('/api/v1/geofences', { method: 'POST', body: JSON.stringify(data) });
      state.geofences.push(zone);
      state.geofences.sort((first, second) => first.name.localeCompare(second.name));
      closeModal(); renderGeofences(); toast('Geofence created.');
    } catch (error) { toast(error.message, 'error'); }
  });
}

function renderPayments() {
  const query = ($('#payment-search').value || '').trim().toLowerCase();
  const period = $('#payment-period').value;
  const days = { daily: 1, weekly: 7, monthly: 30, yearly: 365 }[period];
  const cutoff = days ? Date.now() - days * 86400000 : 0;
  const periodPayments = state.payments.filter((payment) => new Date(payment.paidAt).getTime() >= cutoff);
  const visible = periodPayments.filter((payment) => [payment.payerName, payment.category, payment.paymentMethod, payment.reference, payment.currency].some((value) => String(value || '').toLowerCase().includes(query)));
  const totals = new Map();
  for (const payment of periodPayments) totals.set(payment.currency, (totals.get(payment.currency) || 0) + Number(payment.amount));
  $('#payment-count').textContent = periodPayments.length;
  $('#payment-totals').textContent = [...totals].map(([currency, amount]) => `${currency} ${amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`).join(' · ') || '—';
  $('#payment-latest').textContent = periodPayments[0] ? dateTime(periodPayments[0].paidAt) : '—';
  $('#payment-rows').innerHTML = visible.map((payment) => `<tr><td>${esc(dateTime(payment.paidAt))}</td><td>${esc(payment.payerName)}</td><td>${esc(payment.currency)} ${Number(payment.amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td><td>${esc(payment.category)}</td><td>${esc(payment.paymentMethod.replaceAll('_', ' '))}</td><td>${esc(payment.reference || '—')}</td><td>${esc(payment.recordedBy)}</td></tr>`).join('');
  $('#payment-empty').classList.toggle('hidden', visible.length > 0);
  const canRecord = state.operator?.role === 'ADMIN';
  $('#payment-form').classList.toggle('hidden', !canRecord);
}

async function recordPayment(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const data = Object.fromEntries(new FormData(form).entries());
  data.amount = Number(data.amount);
  data.paidAt = new Date(data.paidAt).toISOString();
  try {
    const payment = await api('/api/v1/billing/payments', { method: 'POST', body: JSON.stringify(data) });
    state.payments.unshift(payment);
    form.reset();
    form.elements.currency.value = 'KES';
    renderPayments();
    toast('Payment recorded and audit logged.');
  } catch (error) { toast(error.message, 'error'); }
}

function settingsDialog() {
  const mapsConfigured = Boolean(state.config.googleMapsApiKey);
  const content = `<div class="modal-copy"><strong>Live map</strong><br>${mapsConfigured ? 'Google Maps configured for this workspace.' : 'OpenStreetMap street tiles are active. Google Maps activates when a restricted browser key is configured.'}<br><br><strong>Vehicle commands</strong><br>Disabled. A device-specific command adapter and safety policy have not been selected.<br><br><strong>Tracker activation SMS</strong><br>${state.config.trackerSmsActivationEnabled ? `${esc(state.config.trackerSmsProvider)} is configured for TK103-compatible devices at ${esc(state.config.trackerActivationServer)}.` : 'Not configured. Set Africa’s Talking credentials, a public fleet domain, the server port, and the TK103 tracker command password before activation.'}<br><br><strong>SMS / email / WhatsApp</strong><br>${state.config.messagingEnabled ? 'A provider is configured. Delivery availability is controlled by the administrator.' : 'Provider not configured. Message drafts may be opened in the operator’s messaging client; Relay does not transmit them.'}<br><br><strong>Device ingestion</strong><br>Authenticated tracker packets only. Each enrolled device receives a one-time credential; configure a provider/device before expecting readings.<br><br><strong>Signed in as</strong><br>${esc(state.operator?.email)} · ${esc(state.operator?.role)}<div class="modal-actions"><button class="button quiet-button" id="change-password">Change password</button>${state.operator?.role === 'ADMIN' ? '<button class="button quiet-button" id="manage-operators">Manage operators</button>' : ''}<button class="button quiet-button" data-close>Close</button></div></div>`;
  modal('Workspace integration status', 'No provider is reported as active until its credentials are configured.', content);
  $('#change-password').addEventListener('click', passwordDialog);
  $('#manage-operators')?.addEventListener('click', operatorsDialog);
}

function passwordDialog() {
  const content = `<form class="modal-form"><label>Current password<input name="currentPassword" type="password" autocomplete="current-password" required></label><label>New password (14+ characters)<input name="newPassword" type="password" autocomplete="new-password" minlength="14" maxlength="200" required></label><div class="modal-actions"><button type="button" class="button quiet-button" data-close>Cancel</button><button type="submit" class="button primary-button">Update password</button></div></form>`;
  modal('Change operator password', 'Your current credential is required.', content, async (form) => {
    try {
      await api('/api/v1/auth/password', { method: 'POST', body: JSON.stringify(Object.fromEntries(form.entries())) });
      closeModal(); toast('Password updated.');
    } catch (error) { toast(error.message, 'error'); }
  });
}

async function operatorsDialog() {
  try {
    const operators = await api('/api/v1/operators');
    const content = `<div class="modal-copy"><div class="modal-actions" style="margin:0 0 12px"><button id="provision-operator" class="button primary-button">+ Provision operator</button></div>${operators.map((operator) => `<div class="trip-row"><div><strong>${esc(operator.email)}</strong><small>${esc(operator.role)}</small></div><span class="status-pill ${operator.enabled ? '' : 'retired'}">${operator.enabled ? 'Enabled' : 'Disabled'}</span>${operator.email.toLowerCase() === state.operator.email.toLowerCase() ? '<span>Current account</span>' : `<button class="button quiet-button" data-toggle-operator="${operator.id}" data-enabled="${operator.enabled}">${operator.enabled ? 'Disable' : 'Enable'}</button>`}</div>`).join('') || '<p>No operator accounts.</p>'}<div class="modal-actions"><button class="button quiet-button" data-close>Close</button></div></div>`;
    modal('Operator accounts', 'Named accounts, roles and access status.', content);
    $('#provision-operator').addEventListener('click', operatorProvisionDialog);
    $$('[data-toggle-operator]').forEach((button) => button.addEventListener('click', async () => {
      try {
        await api(`/api/v1/operators/${button.dataset.toggleOperator}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled: button.dataset.enabled !== 'true' }) });
        await operatorsDialog();
        toast('Operator access updated.');
      } catch (error) { toast(error.message, 'error'); }
    }));
  } catch (error) { toast(error.message, 'error'); }
}

function operatorProvisionDialog() {
  const content = `<form class="modal-form"><label>Operator email<input name="email" type="email" maxlength="320" required></label><label>Role<select name="role"><option value="DISPATCHER">Dispatcher</option><option value="VIEWER">Viewer</option><option value="ADMIN">Administrator</option></select></label><p class="modal-info">A random temporary password is shown once. Share it only through your approved secure channel; the operator should change it on first sign-in.</p><div class="modal-actions"><button type="button" class="button quiet-button" data-close>Cancel</button><button type="submit" class="button primary-button">Provision account</button></div></form>`;
  modal('Provision operator', 'Create a named, role-based account.', content, async (form) => {
    try {
      const result = await api('/api/v1/operators', { method: 'POST', body: JSON.stringify(Object.fromEntries(form.entries())) });
      const credential = `<div class="modal-copy">Account: <strong>${esc(result.email)}</strong><br>Role: <strong>${esc(result.role)}</strong><br>Temporary password (shown once):<code class="one-time-token">${esc(result.oneTimePassword)}</code><p class="modal-warning">Record this credential securely. It cannot be retrieved again.</p><div class="modal-actions"><button class="button quiet-button" id="copy-password">Copy</button><button class="button primary-button" data-close>Done</button></div></div>`;
      modal('Operator provisioned', 'Deliver the temporary credential through an approved channel.', credential);
      $('#copy-password').addEventListener('click', async () => { try { await navigator.clipboard.writeText(result.oneTimePassword); toast('Temporary password copied.'); } catch { toast('Clipboard unavailable. Select the credential and copy it securely.', 'error'); } });
    } catch (error) { toast(error.message, 'error'); }
  });
}

function messageDialog(vehicle) {
  const channels = ['sms', 'gmail', 'whatsapp'];
  const content = `<form class="modal-form"><label>Draft for<select name="channel">${channels.map((channel) => `<option value="${channel}">${channel === 'gmail' ? 'Gmail' : channel[0].toUpperCase() + channel.slice(1)}</option>`).join('')}</select></label><label>Recipient<input name="recipient" required maxlength="254" placeholder="Phone number or email address"></label><label>Message<textarea name="message" required maxlength="1200">Hello ${esc(vehicle.driverName)}, please confirm your status for ${esc(vehicle.vehicleCode)}. Last device reading: ${esc(dateTime(vehicle.lastSeenAt))}.</textarea></label><p class="modal-info">This creates a prefilled draft in the selected app. There is no connected message provider, so Relay will not transmit it.</p><div class="modal-actions"><button type="button" class="button quiet-button" data-close>Cancel</button><button type="submit" class="button primary-button">Open draft ↗</button></div></form>`;
  modal(`Contact ${vehicle.driverName}`, `${vehicle.vehicleCode} · explicit operator draft`, content, async (form) => {
    const data = Object.fromEntries(form.entries());
    const recipient = data.recipient.trim();
    const text = data.message.trim();
    let url;
    if (data.channel === 'gmail') url = `https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(recipient)}&su=${encodeURIComponent(`Fleet update · ${vehicle.vehicleCode}`)}&body=${encodeURIComponent(text)}`;
    else if (data.channel === 'whatsapp') url = `https://wa.me/${recipient.replace(/\D/g, '')}?text=${encodeURIComponent(text)}`;
    else url = `sms:${encodeURIComponent(recipient)}?body=${encodeURIComponent(text)}`;
    window.open(url, '_blank', 'noopener,noreferrer');
    closeModal();
    toast('Draft opened; verify recipient and send from your messaging app.');
  });
}

function confirmTripCancel(id) {
  const trip = state.trips.find((item) => item.id === id);
  if (!trip) return;
  modal('Cancel planned trip?', `${trip.tripCode} · ${trip.vehicleCode}`, `<div class="modal-copy">The trip will remain in the audit trail with a cancelled state.<div class="modal-actions"><button class="button quiet-button" data-close>Keep trip</button><button id="cancel-trip-confirm" class="button primary-button">Cancel trip</button></div></div>`);
  $('#cancel-trip-confirm').addEventListener('click', async () => {
    try { const updated = await api(`/api/v1/trips/${id}/cancel`, { method: 'PATCH' }); state.trips = state.trips.map((item) => item.id === id ? updated : item); closeModal(); renderTrips(); toast('Trip cancelled.'); }
    catch (error) { toast(error.message, 'error'); }
  });
}

function completeTrip(id) {
  const trip = state.trips.find((item) => item.id === id);
  if (!trip) return;
  modal('Complete active trip?', `${trip.tripCode} · ${trip.vehicleCode}`, `<div class="modal-copy">Confirm the driver has completed this assignment. This action is recorded in the audit trail.<div class="modal-actions"><button class="button quiet-button" data-close>Keep active</button><button id="complete-trip-confirm" class="button primary-button">Complete trip</button></div></div>`);
  $('#complete-trip-confirm').addEventListener('click', async () => {
    try { const updated = await api(`/api/v1/trips/${id}/complete`, { method: 'PATCH' }); state.trips = state.trips.map((item) => item.id === id ? updated : item); closeModal(); renderTrips(); toast('Trip completed.'); }
    catch (error) { toast(error.message, 'error'); }
  });
}

function bind() {
  $('#login-form').addEventListener('submit', login);
  $('#sign-out').addEventListener('click', () => signOut(true));
  $('#settings-open').addEventListener('click', settingsDialog);
  $('#top-settings').addEventListener('click', settingsDialog);
  $$('.rail-link[data-view]').forEach((button) => button.addEventListener('click', () => navigate(button.dataset.view)));
  $$('[data-view="exceptions"], [data-view="trips"]').forEach((button) => button.addEventListener('click', () => navigate(button.dataset.view)));
  $$('.enroll-open').forEach((button) => button.addEventListener('click', () => vehicleDialog()));
  $('#trip-plan').addEventListener('click', planTripDialog);
  $('#new-trip').addEventListener('click', planTripDialog);
  $('#map-fit').addEventListener('click', fitMap);
  $('#map-layer').addEventListener('click', switchLayer);
  $('#geofence-create').addEventListener('click', geofenceDialog);
  $('#payment-form').addEventListener('submit', recordPayment);
  $('#payment-search').addEventListener('input', renderPayments);
  $('#payment-period').addEventListener('change', renderPayments);
  $('#fleet-search').addEventListener('input', (event) => { state.search = event.target.value; renderFleet(); });
  $('#fleet-status').addEventListener('change', (event) => { state.status = event.target.value; renderFleet(); });
  $$('[data-action="export"]').forEach((button) => button.addEventListener('click', exportCsv));
  $$('[data-action="export-report"]').forEach((button) => button.addEventListener('click', () => exportReport().catch((error) => toast(error.message, 'error'))));
  document.addEventListener('click', (event) => {
    const view = event.target.closest('[data-view]');
    if (view && !view.classList.contains('rail-link')) { navigate(view.dataset.view); return; }
    const focus = event.target.closest('[data-focus-vehicle]');
    if (focus) { focusVehicle(focus.dataset.focusVehicle); return; }
    const edit = event.target.closest('[data-edit-vehicle]');
    if (edit) { const vehicle = currentVehicle(edit.dataset.editVehicle); if (vehicle) vehicleDialog(vehicle); return; }
    const remove = event.target.closest('[data-delete-vehicle]');
    if (remove) { const vehicle = currentVehicle(remove.dataset.deleteVehicle); if (vehicle) deleteVehicleDialog(vehicle); return; }
    const activate = event.target.closest('[data-activate-vehicle]');
    if (activate) { activateTrackerDialog(activate.dataset.activateVehicle); return; }
    const contact = event.target.closest('[data-message-vehicle]');
    if (contact) { const vehicle = currentVehicle(contact.dataset.messageVehicle); if (vehicle) messageDialog(vehicle); return; }
    const cancel = event.target.closest('[data-cancel-trip]');
    if (cancel) confirmTripCancel(cancel.dataset.cancelTrip);
    const complete = event.target.closest('[data-complete-trip]');
    if (complete) completeTrip(complete.dataset.completeTrip);
    const archiveGeofence = event.target.closest('[data-archive-geofence]');
    if (archiveGeofence) {
      api(`/api/v1/geofences/${archiveGeofence.dataset.archiveGeofence}/archive`, { method: 'PATCH' }).then(() => {
        state.geofences = state.geofences.map((zone) => zone.id === archiveGeofence.dataset.archiveGeofence ? { ...zone, enabled: false } : zone);
        renderGeofences(); toast('Geofence archived.');
      }).catch((error) => toast(error.message, 'error'));
    }
  });
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) state.streamAbort?.abort();
    else if (state.operator) connectLiveFeed();
  });
  setInterval(() => { $('#utc-clock').textContent = `UTC ${new Date().toISOString().slice(11,16)}`; renderKpis(); }, 30000);
}

bind();
$('#utc-clock').textContent = `UTC ${new Date().toISOString().slice(11,16)}`;