# ⚡ Meter Load Manager

Track and manage 3 IESCO electricity meters (600, 603, 700). Stay below the 200-unit
protected slab, get switch recommendations, and export billing history.

---

## 📲 Install the APK (no Android Studio needed)

> **GitHub Actions automatically builds the APK every time you push.**

1. Go to the **Actions** tab on GitHub
2. Click the latest **Build APK** run
3. Scroll to **Artifacts** at the bottom
4. Download **MeterLoadManager-debug**
5. Unzip → install `app-debug.apk` on your Android phone
   *(Settings → Apps → Install unknown apps → allow your file manager)*

---

## 🚀 Push to GitHub (one time setup)

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/MeterLoadManager.git
git push -u origin main
```

The Actions build starts automatically. Wait ~5 minutes, then download the APK.

---

## Features

| Tab | What it does |
|-----|-------------|
| **Dashboard** | Current cycle stats, meter cards, progress bars, switch recommendations |
| **Add Reading** | Log a new meter reading with date/time picker |
| **History** | 13 months of billing history per meter, filterable, editable |
| **Analytics** | Averages, cost/unit, over-200 months, bar charts, Meter 700 tariff warning |
| **Switches** | Change meter status, log switch events, view recommendations |
| **Export** | Share as CSV, JSON backup, or full text report |

## Threshold Rules

| Units | Risk | Action |
|-------|------|--------|
| 0–149 | 🟢 Safe | Continue normally |
| 150 | 🟡 Watch | Turn on Meter 700, reduce load from 600 |
| 180 | 🟠 Warning | Immediately reduce load |
| 190 | 🔴 Danger | Stop or minimize this meter |
| 199+ | ⛔ Critical | Do NOT use before next billing cycle |
| 200+ | ❌ Slab | Higher tariff – needs 6 months to reset |

## Meters

| Meter | Reference | Status |
|-------|-----------|--------|
| 600 | 03 14622 1335600 | Running (primary) |
| 603 | 03 14622 1335603 | Sharing |
| 700 | 03 14622 1335700 | Paused (turn on when M600 hits 150 units) |

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- Room database (offline, no login)
- MVVM + Repository pattern
- GitHub Actions CI/CD → APK artifact
