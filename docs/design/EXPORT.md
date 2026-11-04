# Экспорт оригинального бренда

Python 3.12, CairoSVG 2.8.2, Pillow 11.3.0; Cairo системной среды.
Из корня репозитория:

```sh
python3 -m venv work/asset-venv
work/asset-venv/bin/pip install CairoSVG==2.8.2 Pillow==11.3.0
work/asset-venv/bin/python scripts/export_brand.py
work/asset-venv/bin/python scripts/check_foundation.py
```

SVG — редактируемые оригиналы; PNG — их экспорты. XML фирменного знака
использует ту же геометрию в Android VectorDrawable и ссылки на color resources.
Android resources проверяются lint/resource compiler; автоматической синхронизации
при будущей ручной правке вектора нет — нужно обновлять SVG и VectorDrawable вместе.
