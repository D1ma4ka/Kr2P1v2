# Дизайн-система — цель v1.3 (Dark v1.2)

Эта документация заменяет прежнее предложенное оформление Craft Mark.
P3 обновляет тему, исходные SVG/PNG/Android icons и добавляет sensor liquid.
Исторические результаты P1 сохранены в Git; реальные P3 проверки — stages/P3.md.
Референс reference/krapiva-dark-concept.png — обязательный стилевой ориентир,
не готовый asset, не данные и не разрешение копировать чужие картинки.

| Токен | Назначение | HEX |
|---|---|---|
| `color.background.primary` | основной фон, почти чёрный графит | `#0B0D0F` |
| `color.background.secondary` | вторичный фон | `#171A1D` |
| `color.surface.card` | карточки | `#1D2125` |
| `color.surface.raised` | дополнительная поверхность | `#252A2E` |
| `color.text.primary` | основной текст, тёплый кремовый | `#F5EBD8` |
| `color.text.secondary` | вторичный текст, серо-бежевый | `#AAA69F` |
| `color.accent.primary` | главный янтарный акцент, CTA, оценка | `#F5A623` |
| `color.accent.hop` | зелёный хмель, награды, вспомогательные акценты | `#78B936` |
| `color.accent.hop-light` | локальное подсвечивание зелёных деталей | `#9ACD45` |
| `color.border.divider` | ненавязчивые границы и разделители | `#303438` |

Янтарь — главный CTA/active акцент, зелёный — хмель/отдельные badge детали.
Semantic disabled/focus/error/warning/success определить и проверить на контраст
при UI-реализации. Коррекции утверждённых цветов только по доказанной проблеме
доступности с документированным сравнением. Фиксированная Dark; light mode вне MUST.

4dp-сетка, интервалы 4/8/12/16/24/32dp, радиусы 12/16/20dp, touch target ≥48dp.
Современный sans-serif Manrope/Inter с проверкой прав или системный fallback;
не распространять SF Pro в Android. Кремовый текст, серо-бежевый вторичный,
графитовые карточки, спокойные разделители, единые линейные иконки.

Требуемые компоненты: position/checkin cards, profile header, rating input,
badges, empty/error/photo picker/venue row, amber button, bottom bar и beer hero.
Они появляются вместе с реальными состояниями, не фиктивными успешными API.
На Android TalkBack/font scaling/insets/keyboard/back; на iOS VoiceOver,
Dynamic Type/safe areas. Визуальный baseline: home/catalog/checkin/profile/badges
плюс loading/onboarding, реальные screenshots после реализации, сейчас NOT RUN.

Motion: статичный system splash → custom beer loading лишь при реальном ожидании;
welcome/registration hero не закрывает форму. Gravity/accelerometer, gyro optional;
плавность/clamp, ориентация, no-sensor loop, reduced-motion static. Ориентиры
±6–8°/около30Hz не жёсткие параметры. Нет фоновых sensors/логов/отправки на сервер.
ADR-MOTION и unit/UI/physical tests — P3/P8, iOS эквивалент — P12/P13.

## Реализованное состояние P3

Theme.kt и tokens.json используют палитру выше; system sans-serif.
Error #FFB4AB, focus/warning amber, success hop-light; disabled muted с
платформенной disabled opacity. Оригинальный знак: бокал/пена/хмелевая шишка/
рамка сканера, горизонтальный KraPiva. Конкретные результаты — ASSET_INVENTORY.
Доступные auth/profile/settings экраны используют тему; остальные появляются
только с реальными функциями P4–P7. Нижняя композиция пяти пунктов зафиксирована,
доступен профиль; будущие назначения disabled, никаких имитаций server flows.

## Реализованное состояние P4

CatalogScreen использует KraPiva Dark/общие typography и radii, общий
BeerPositionCard, original mark вместо отсутствующего фото, источники/статусы,
пятиэлементную нижнюю навигацию с активным каталогом. Server empty/error и offline
состояния видимы; нет демонстрационных рейтингов/чекинов. Scanner cream corners,
permission/manual fallback; proposal form использует native picker. SVG/иконки
P3 сохранены. Actual screenshot baseline дополняется после device acceptance.
