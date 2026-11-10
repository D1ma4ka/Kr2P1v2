# П6.1 — Craft Club: реализованный кандидат Light/Dark

Актуальная реализация — `333085b`; [карта экранов и ограничения](P61_COMPLETE_CANDIDATE.md). Палитры `Theme.kt`: Dark background `#101315`, surface `#1D2226`, text `#F4EEE4`; Light background `#F7F5F0`, surface `#FFFFFF`, text `#202322`. Фирменная amber CTA `#F3AF45` / text `#171B1C`; Light текстовые primary-действия `#935500` для читаемости. Общие ClubCard/ClubHeader/ClubPhoto/ClubNavigation и локальные контурные иконки используются во всех основных разделах. Неактивный бренд не увеличивается вместе с font scale; пользовательский контент масштабируется, подписи вкладок допускают две строки.

Использованы все семь пар макетов; screenshots работающего кандидата ещё не получены. Бокал/пена/пузырьки реализованы локально в Canvas, не фотографически точная копия. Существующий сенсорный hero сохранён в регистрации. Reduced motion останавливает новый reveal на конечном кадре; фон/уход с экрана прекращает frame job. Данные/рейтинги/фото из макетов в приложение не зашиты. Приёмка 320dp/крупного шрифта/TalkBack и pixel comparison PENDING.

Ниже сохранена история предыдущей дизайн-системы.

---

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
доступности с документированным сравнением. Исторически до П6.1 использовалась фиксированная Dark. **Дополнение П6.1 от 10.11.2026 меняет требование: обе темы Light/Dark обязательны для обновлённого Android UI.** Конкретные цвета Light берутся из 14 одобренных макетов после их верификации, не придумываются по старой Dark-палитре. См. [этап П6.1](../stages/P6.1.md).

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
P3 сохранены. Actual API35 baseline: [P4 JPEG](p4-review.jpg),
[original PNG hashes](p4-screenshot-manifest.json), CI42292450630 SUCCESS.
PreviewView220dp ограничен native bounds и Compose clip; manual controls читаемы.


## Реализованное состояние P5

DiaryCard/BeerPositionCard/Profile используют общие KraPiva graphite/amber/cream
tokens. Интерактивные style/brewery bars и20 rating bins имеют текстовые counts,
подписанный denominator, unknown/unrated/empty состояния; цвет не единственный
канал. Editor/photo picker/local dates, unsent drafts/offline notices и real home
имеют baseline p5-review.jpg + original PNG hash manifest. Полная physical
TalkBack/font-scale regression всех новых экранов остаётся P8/NOT RUN.
