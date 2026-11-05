# Ресурсы KraPiva Dark — P3

| Ресурс | Файл | Результат |
|---|---|---|
| Референс владельца | reference/krapiva-dark-concept.png | Только документация, исходный hash сохранён |
| Оригинальный знак | assets/brand/mark.svg + PNG512 | Бокал/пена/хмель/сканер, собственная геометрия |
| Горизонтальный логотип | assets/brand/logo.svg + PNG940×240 | Знак слева, KraPiva справа, системный sans |
| Placeholder аватара | assets/brand/avatar.svg + PNG256 | Craft beer, без чужих фото |
| Android/splash/adaptive/monochrome | android/app/src/main/res/drawable, mipmap* | Vector safe region и пять PNG-плотностей |
| Semantic tokens | assets/brand/tokens.json, Theme.kt | Утверждённая палитра + focus/error/warning/success/disabled |
| Liquid hero | designsystem/BeerHero.kt, LiquidMotion.kt | Canvas clip, отдельная жидкость/пена, local-only sensors |
| Скриншоты | Результаты перечисляются в VISUAL_BASELINE.md | Только реальные состояния эмулятора; не макеты |

Шрифтовые файлы не добавлены: Android system sans, SVG system fallback.
Никаких старых каталогов, фотографий или данных из референса. Home/catalog/
checkin/badges screenshots появятся с реальными функциями P4–P7, не имитируются.
`check_foundation.py`: dimensions/XML/SVG/wrapper/hygiene/contrast PASS.
Основной текст/фон 16.46:1; muted/card 6.68:1, amber/ink 9.61:1.
