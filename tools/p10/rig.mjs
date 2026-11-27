const rest = { x: 0, y: 0, rotation: 0, scaleX: 1, scaleY: 1 }
const unit = [1, 0, 0, 1, 0, 0]

function multiply([a, b, c, d, x, y], [e, f, g, h, u, v]) {
  return [
    a * e + c * f, b * e + d * f,
    a * g + c * h, b * g + d * h,
    a * u + c * v + x, b * u + d * v + y
  ]
}

function around([px, py], { x = 0, y = 0, rotation = 0, scaleX = 1, scaleY = 1 }) {
  const radians = rotation * Math.PI / 180
  const cos = Math.cos(radians)
  const sin = Math.sin(radians)
  const a = cos * scaleX
  const b = sin * scaleX
  const c = -sin * scaleY
  const d = cos * scaleY
  return [a, b, c, d, px + x - a * px - c * py, py + y - b * px - d * py]
}

function sample(keys, time, defaults) {
  if (!keys?.length) return defaults
  if (time <= keys[0].time) return { ...defaults, ...keys[0] }
  const next = keys.findIndex(key => key.time > time)
  if (next < 0) return { ...defaults, ...keys.at(-1) }

  const left = keys[next - 1]
  const right = keys[next]
  const fraction = (time - left.time) / (right.time - left.time)
  return Object.fromEntries(Object.entries(defaults).map(([field, fallback]) => {
    const start = left[field] ?? fallback
    const end = right[field] ?? fallback
    return [field, typeof start === 'number' ? start + (end - start) * fraction : start]
  }))
}

export function pose(rig, clipName, elapsed = 0, { room = false } = {}) {
  const clip = rig.clips[clipName]
  const time = clip
    ? clip.loop ? Math.max(0, elapsed) % clip.duration : Math.min(clip.duration, Math.max(0, elapsed))
    : 0

  const bones = {}
  function resolve(name) {
    if (bones[name]) return bones[name]
    const bone = rig.bones[name]
    const parent = bone.parent ? resolve(bone.parent) : unit
    const key = sample(clip?.tracks?.[name], time, rest)
    const towardTable = room && name === 'root' && ['sit', 'eat'].includes(clipName)
    const local = around(bone.pivot, towardTable ? { ...key, x: -key.x } : key)
    return bones[name] = multiply(parent, local)
  }
  for (const name of Object.keys(rig.bones)) resolve(name)

  const layers = Object.entries(rig.layers).map(([name, layer]) => {
    const variant = sample(clip?.variants?.[name], time, { value: 'open' }).value
    return {
      ...layer,
      ...layer.variants?.[variant],
      transform: bones[layer.bone]
    }
  }).sort((first, second) => first.depth - second.depth)

  return { bones, layers }
}

export function placeItem(rig, frame, slot, item = {}) {
  const anchor = rig.anchors[slot]
  if (!anchor) return null
  const [offsetX, offsetY] = item.offset ?? [0, 0]
  const [originX, originY] = item.origin ?? [0, 0]
  const [x, y] = anchor.position
  const scale = anchor.scale * (item.scale ?? 1)

  const local = around([0, 0], {
    x: x + offsetX,
    y: y + offsetY,
    rotation: anchor.rotation + (item.rotation ?? 0),
    scaleX: scale * (anchor.scaleX ?? 1),
    scaleY: scale * (anchor.scaleY ?? 1)
  })
  return {
    depth: item.depth ?? anchor.depth,
    hemScale: anchor.hemScale ?? 1,
    transform: multiply(multiply(frame.bones[anchor.bone], local), [1, 0, 0, 1, -originX, -originY])
  }
}

export function drawList(rig, frame, equipped) {
  const layers = [...frame.layers]
  for (const item of equipped) {
    const fit = item.fits?.[rig.id]
    if (!fit) continue
    for (const piece of fit) {
      const placed = placeItem(rig, frame, piece.slot, piece)
      if (placed) layers.push({
        ...placed,
        src: piece.src,
        offset: [0, 0]
      })
    }
  }
  return layers.sort((first, second) => first.depth - second.depth)
}
