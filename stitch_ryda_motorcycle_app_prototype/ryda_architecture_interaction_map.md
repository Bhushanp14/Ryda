# RYDA — Interactive Prototype Architecture & Motion Specification

## 1. Product Identity & Platform Philosophy
- **Name:** RYDA
- **Platform:** Android-first (Engineered for Jetpack Compose & Kotlin MVVM implementation)
- **Target Audience:** Indian motorcycle enthusiasts (Tourers, Commuters, Adventure Riders, Weekend Riders) across Mumbai, Pune, Nashik, Goa, Bengaluru, Hyderabad, and Delhi.
- **Core Principle:** Motorcycle riding is at the heart of RYDA. Telemetry and tracking remain dominant; social and club elements elevate the riding experience without distraction.

---

## 2. Design System Tokens (`DESIGN.md` & `tailwind.config.js`)
- **Theme Polarity:** Dark-first (`#101114` Asphalt Black, `#191B20` Surface Container, `#22252B` High Surface).
- **Primary Brand Accent:** `#FF6B35` Moto Orange (Tachometer redline energy, high visibility).
- **Telemetry Cyan:** `#00E5FF` (GPS tracking, satellite locks, and speed indicators).
- **Typography:**
  - Headlines & Brand: **Plus Jakarta Sans** (Geometric, crisp, modern).
  - Body & Microcopy: **Instrument Sans** (High legibility on mobile screens).
  - Telemetry & Speedometer: **Source Code Pro** (Tabular figures for stable, non-jumping telemetry readouts).

---

## 3. Motion System Architecture
RYDA implements a purposeful motion vocabulary respecting the 100ms–600ms timing guidelines:
1. **120ms Spring Tap Press (`duration-tap-press`):** Applied to primary controls (Start Ride, Pause, Save, Likes). Rapid scale compression (0.95) with immediate tactile feedback.
2. **200ms Control Expand (`duration-control-expand`):** Smooth dropdown, search expansion, and category switches.
3. **300ms Bottom Sheet Slide (`duration-sheet-slide`):** Notification drawers, filter sheets, and ride publishing modal dialogs.
4. **600ms Celebratory Badge Unlock (`duration-badge-unlock`):** Rotational scaling, particle burst, and fanfare milestone unlocks.
5. **GPS Radar Animation:** Concentric 2.5s radar wave indicating satellite acquisition states without generic spinners.
6. **Progressive Route Draw:** SVG polyline drawing simulation for recorded GPX paths.

---

## 4. Screen Architecture & Canvas Map

| Screen | Route / File | Core Interactions & Capabilities |
| :--- | :--- | :--- |
| **Home Dashboard** | `screens/home-dashboard/HomeDashboard.html` | Hero Start Ride trigger, telemetry odometer, recent ride card, upcoming rally card, interactive notification drawer. |
| **Active Ride Tracking** | `screens/active-ride-tracking/ActiveRideTracking.html` | Full-screen vector dark map, live heading beacon, progressive polyline drawing, dynamic speedometer (64 km/h), tactile Pause/Resume/Finish controls, haptic toast simulator. |
| **Completed Ride Summary** | `screens/completed-ride-summary/CompletedRideSummary.html` | Route thumbnail with Thane-Lonavala elevation profile, stat pop count-ups, GPX export generator, event linkage dropdown, community publish switch. |
| **Ride History & Analytics** | `screens/ride-history/RideHistory.html` | Date-grouped ride history (Nov/Oct 2026), motorcycle tag filters, distance and moving time telemetry metrics. |
| **Motorcycle Garage** | `screens/motorcycle-garage/MotorcycleGarage.html` | Collection cards for RE Himalayan 450 & Yezdi Roadster, primary ride indicators, modal dialog to add new motorcycle. |
| **Rider Profile & Badges** | `screens/rider-profile/RiderProfile.html` | Cover banner, verified rider badge, 58 rides logged, 12 achievement badges with progress bars (5,000 KM milestone, State Explorer). |
| **Community Feed & Social** | `screens/community-feed/CommunityFeed.html` | Segmented feed switch (Following / Discover), ride post cards with verified telemetry, animated heart like micro-interactions, modal post creator. |
| **Explore & Discovery Hub** | `screens/explore-hub/ExploreHub.html` | Expanding searchbar with Western Ghats suggestions, club discovery cards (Mumbai Weekend Riders), nearby riders. |
| **Club Detail & Events** | `screens/club-detail/ClubDetail.html` | Club profile, active member roster (1,420 riders), official Dawn Patrol rally card, interactive RSVP Going/Interested selector. |
| **Motion Showcase** | `screens/motion-showcase/MotionShowcase.html` | Interactive motion lab: Achievement unlock fanfare, 3-state GPS acquisition radar, tactile button spring & haptic intensity mapping. |
