import { drawList, placeItem, pose } from './rig.mjs'

const catalogURL = new URL('../../assets/pets/catalog.json', import.meta.url)
const petSelect = document.querySelector('#pet')
const clipSelect = document.querySelector('#clip')
const play = document.querySelector('#play')
const time = document.querySelector('#time')
const anchors = document.querySelector('#anchors')
const status = document.querySelector('#status')
const contexts = Array.from(document.querySelectorAll('canvas'), canvas => canvas.getContext('2d'))
const artwork = document.createElement('canvas')
const artworkContext = artwork.getContext('2d')
const clipNames = { idle: 'Дыхание', greet: 'Приветствие', blink: 'Моргание', react: 'Реакция' }

let selected
let loadNumber = 0
let timer = 0

async function readJSON(url) {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`${response.status} ${url.pathname}`)
  return response.json()
}

async function readImage(url) {
  const image = new Image()
  image.src = url
  await image.decode()
  return image
}

function drawPins(context, rig, frame) {
  for (const slot of Object.keys(rig.anchors)) {
    const { transform } = placeItem(rig, frame, slot)
    context.save()
    context.transform(...transform)
    context.beginPath()
    context.moveTo(-26, 0)
    context.lineTo(26, 0)
    context.moveTo(0, -26)
    context.lineTo(0, 26)
    context.strokeStyle = '#161b24'
    context.lineWidth = 13
    context.stroke()
    context.strokeStyle = '#fff'
    context.lineWidth = 6
    context.stroke()
    context.restore()
  }
}

function render() {
  if (!selected) return
  const { rig, images } = selected
  const clip = clipSelect.value
  const frame = pose(rig, clip, time.valueAsNumber)
  const layers = clip ? drawList(rig, frame, []) : [{ ...rig.neutral, transform: [1, 0, 0, 1, 0, 0] }]
  const [artWidth, artHeight] = rig.canvas
  if (artwork.width !== artWidth || artwork.height !== artHeight) {
    artwork.width = artWidth
    artwork.height = artHeight
  }
  artworkContext.clearRect(0, 0, artWidth, artHeight)
  for (const { src, offset, transform } of layers) {
    artworkContext.setTransform(...transform)
    artworkContext.drawImage(images.get(src), ...offset)
  }
  artworkContext.resetTransform()
  if (anchors.checked) drawPins(artworkContext, rig, frame)
  for (const context of contexts) {
    const { width, height } = context.canvas
    const factor = .91 * Math.min(width / artWidth, height / artHeight)
    const displayWidth = artWidth * factor
    const displayHeight = artHeight * factor
    context.clearRect(0, 0, width, height)
    context.drawImage(artwork, (width - displayWidth) / 2, (height - displayHeight) / 2,
      displayWidth, displayHeight)
  }
}

function stop() {
  cancelAnimationFrame(timer)
  timer = 0
  play.textContent = 'Проиграть'
}

function changeClip() {
  stop()
  time.value = '0'
  time.max = selected?.rig.clips[clipSelect.value]?.duration ?? 0
  time.disabled = play.disabled = time.max === '0'
  render()
}

async function selectPet(record) {
  const request = ++loadNumber
  stop()
  play.disabled = time.disabled = clipSelect.disabled = true
  status.textContent = 'Загружаем ресурсы…'
  const { rig, url } = record
  const refs = [rig.neutral, ...Object.values(rig.layers).flatMap(layer =>
    [layer, ...Object.values(layer.variants ?? {})])]
  const images = new Map(await Promise.all(refs.map(async ({src}) =>
    [src, await readImage(new URL(src, url))])))
  if (request !== loadNumber) return
  selected = { rig, images }
  clipSelect.replaceChildren(new Option('Покой', ''),
    ...Object.keys(rig.clips).map(clip => new Option(clipNames[clip] ?? clip, clip)))
  clipSelect.disabled = false
  status.textContent = ''
  changeClip()
}

function showError(error) {
  status.textContent = `Ошибка загрузки графики. ${error.message}`
}

try {
  const catalog = await readJSON(catalogURL)
  const records = await Promise.all(catalog.pets.map(async pet => {
    const url = new URL(pet.rig, catalogURL)
    return { url, rig: await readJSON(url) }
  }))
  petSelect.replaceChildren(...records.map(({ rig }) => new Option(rig.name, rig.id)))
  petSelect.disabled = false
  petSelect.addEventListener('change', () => selectPet(records[petSelect.selectedIndex]).catch(showError))
  await selectPet(records[0])
} catch (error) {
  showError(error)
}

clipSelect.addEventListener('change', changeClip)
time.addEventListener('input', () => { stop(); render() })
anchors.addEventListener('change', render)
document.addEventListener('visibilitychange', () => { if (document.hidden) stop() })
play.addEventListener('click', () => {
  if (timer) return stop()
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) {
    status.textContent = 'Включено уменьшенное движение. Кадры доступны через ползунок.'
    return
  }
  status.textContent = ''
  play.textContent = 'Пауза'
  const start = performance.now()
  const duration = Number(time.max)
  function tick(timestamp) {
    time.value = String(Math.min(duration, Math.round(timestamp - start)))
    render()
    if (timestamp - start < duration) timer = requestAnimationFrame(tick)
    else stop()
  }
  timer = requestAnimationFrame(tick)
})
