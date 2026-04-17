# PesaTrack Website & App Visibility Plan

## Overview

This plan covers two interconnected goals:
1. **Build a professional website** on a custom domain to serve as PesaTrack's public presence
2. **Increase app visibility** through a multi-channel growth strategy once the Play Store production review is approved

---

## Part 1: Website

### Why a Website

| Benefit | Details |
|---------|---------|
| **Play Store trust signal** | Google reviewers and users see a professional presence beyond the listing |
| **SEO / discoverability** | Captures search traffic for "M-PESA expense tracker", "track mpesa spending", etc. |
| **Deep link target** | Play Store listing links to it; social media posts drive traffic to it |
| **Content hub** | Blog posts, guides, changelogs — all indexable by Google |
| **SMS permission justification** | A clear page explaining *why* SMS access is needed helps with Google's review |

### Domain Selection

| Domain | Price (approx/year) | Registrar | Notes |
|--------|-------------------|-----------|-------|
| **pesatrack.co.ke** | ~KES 1,000–1,500 ($8–12) | [Kenya NIC](https://kenic.or.ke) via Safaricom Domains, Truehost, etc. | Kenyan TLD — strong local SEO signal; shows this is a Kenyan product |
| **pesatrack.app** | ~$14–20 | Namecheap / Cloudflare | `.app` enforces HTTPS (trust signal); tech-savvy feel |
| **pesatrack.com** | ~$10–15 | Namecheap / Cloudflare | Universal, professional; check availability |
| **getpesatrack.com** | ~$10–15 | Any registrar | Fallback if `.com` is taken; clear CTA in the domain |

**Recommendation:** `pesatrack.co.ke` for Kenya-first positioning. Optionally add `pesatrack.app` as a global alias later. The `.co.ke` TLD gives stronger local SEO and costs less.

### Hosting

| Option | Cost | Pros | Cons |
|--------|------|------|------|
| **Cloudflare Pages** | Free | Fastest CDN globally, free SSL, custom domain, Git deploy | Slight learning curve |
| **Vercel** | Free (hobby) | Dead-simple deploy from Git, great DX, free SSL | 100GB bandwidth/month on free tier |
| **Netlify** | Free (starter) | Similar to Vercel, form handling built-in | 100GB bandwidth/month |

**Recommendation:** **Cloudflare Pages** — free, fastest CDN (important for Kenyan mobile networks), free SSL, and easy custom domain setup via Cloudflare DNS. Vercel is the second choice if simplicity is preferred.

### Tech Stack

| Option | Build Time | Flexibility | Best For |
|--------|-----------|-------------|----------|
| **Astro** | Fast | High — supports Markdown, components, zero JS by default | Content-heavy site (blog + docs + landing) |
| **Next.js (static export)** | Medium | Very high — React ecosystem | If future dynamic features are planned |
| **Hugo** | Very fast | Good — Go templates | Pure speed; great for blogs |

**Recommendation:** **Astro** — zero JavaScript shipped by default (fast on Kenyan mobile connections), native Markdown support for blog posts, and easy to add interactivity later. Deploys to Cloudflare Pages or Vercel with one command.

### Site Structure

```
pesatrack.co.ke
├── /                     Landing page — hero, features, screenshots, Play Store badge
├── /privacy-policy       Migrate from current GitHub Pages (docs/privacy-policy.html)
├── /how-it-works         Visual explainer — SMS → parse → categorize → budget
├── /features             Detailed feature breakdown with app screenshots
├── /faq                  Common questions with privacy-focused answers
├── /changelog            Version history (v1.0.0, future releases)
├── /blog/                SEO content hub
│   ├── track-mpesa-spending    "How to Track M-PESA Spending in 2026"
│   ├── mpesa-transaction-costs "Why Your M-PESA Transaction Costs Add Up"
│   └── budget-tips-kenya       "Budgeting Tips for M-PESA Users"
└── /support              Contact info + FAQ link
```

### Landing Page Content

**Hero Section:**
- App icon + "PesaTrack" title
- Tagline: "Track every M-PESA shilling. Automatically. Privately."
- Play Store badge (official Google asset)
- Phone mockup showing the Home screen

**Feature Highlights (3–4 cards):**
1. 📱 **Automatic SMS Tracking** — M-PESA & NCBA transactions detected in real time
2. 🔒 **100% Private** — No internet permission, no servers, no data collection
3. 📊 **Smart Budgets & Analytics** — Category budgets with alerts, monthly/yearly charts
4. 📥 **Import History** — Backfill from past SMS + Excel spreadsheets

**Social Proof Section (add later):**
- Play Store rating + review count
- Download count
- User testimonials

**Footer:**
- Privacy Policy link
- Contact email
- Play Store link
- "Made in Kenya 🇰🇪"

### Setup Steps

1. [ ] **Register domain** — `pesatrack.co.ke` via a Kenyan registrar
2. [ ] **Set up Cloudflare account** — add domain, configure DNS
3. [ ] **Scaffold Astro project** — `npm create astro@latest pesatrack-site`
4. [ ] **Build landing page** — hero, features, screenshots, Play Store badge
5. [ ] **Migrate privacy policy** — port existing `docs/privacy-policy.html` to the new site
6. [ ] **Build supporting pages** — /how-it-works, /features, /faq, /changelog
7. [ ] **Connect to Cloudflare Pages** — Git repo → auto-deploy on push
8. [ ] **Update Play Store listing** — change privacy policy URL to `pesatrack.co.ke/privacy-policy`
9. [ ] **Update in-app About screen** — update URL in `AboutScreen.kt`
10. [ ] **Write first blog post** — "How to Track M-PESA Spending in 2026"

### Cost Summary

| Item | Cost |
|------|------|
| Domain (`.co.ke`) | ~KES 1,000/year (~$8) |
| Hosting (Cloudflare Pages) | Free |
| SSL | Free (Cloudflare) |
| **Total** | **~$8/year** |

---

## Part 2: App Visibility & Growth Strategy

### Phase 1: Pre-Launch (Do Now — While Waiting for Review)

#### App Store Optimization (ASO)

The Play Store listing is already prepared (see `plans/play-store-listing-plan.md`). Additional optimizations:

| Element | Action |
|---------|--------|
| **Title** | Ensure primary keyword: "PesaTrack — M-PESA Expense Tracker" |
| **Keywords in description** | Ensure these appear naturally: *M-PESA tracker, mpesa expense, bank SMS tracker, Kenya finance app, budget tracker Kenya, offline expense tracker, track mpesa spending* |
| **Screenshots** | First 2 must show strongest value: auto-SMS-tracking + privacy |
| **Swahili listing** | Add a Swahili store listing translation — huge for Kenya market discovery |

#### Prepare Launch Content

- [ ] Write 2–3 Twitter/X thread drafts about the build journey
- [ ] Prepare a Product Hunt listing draft (tagline, description, images)
- [ ] Record a screen capture GIF/video of the app detecting an M-PESA SMS in real time
- [ ] Draft LinkedIn post (professional build story)
- [ ] Prepare Reddit posts for r/Kenya, r/androidapps, r/SideProject

### Phase 2: Launch Day (When Production Goes Live)

#### Social Media Blitz

Post simultaneously across all platforms:

| Platform | Strategy |
|----------|----------|
| **Twitter/X** | Thread: "I built PesaTrack because..." + hashtags `#KenyanTech #MpesaTracker #FinTechKenya #BuildInPublic` |
| **Reddit** | r/Kenya, r/androidapps, r/SideProject, r/fintech — different angle for each subreddit |
| **Kenyan Telegram groups** | Silicon Savannah, Nairobi Tech Community, KOT Tech |
| **Facebook** | "Kenyan Developers", "M-PESA Users Kenya", personal finance groups |
| **LinkedIn** | Professional build story — resonates with Kenyan tech community |
| **WhatsApp** | Personal contacts + status updates (WhatsApp is king in Kenya) |

#### Product Hunt Launch

- Tagline: *"PesaTrack — Your M-PESA transactions, tracked automatically. 100% offline."*
- Launch on **Tuesday–Thursday** for maximum visibility
- Include a 60-second demo video
- Respond to every comment on launch day

#### Tech Blog Outreach

| Target | Pitch Angle |
|--------|-------------|
| **TechWeez** | "Kenyan dev builds 100% offline M-PESA tracker — zero data collection" |
| **Dignited** | "PesaTrack: Track every M-PESA shilling without giving up your privacy" |
| **Gadgets Africa** | "New Kenya-first finance app reads your M-PESA SMS locally" |
| **CIO East Africa** | Enterprise / fintech angle |

The **privacy angle** is the strongest pitch — in a market full of data-hungry apps, "no internet permission, no servers, no analytics" is genuinely newsworthy.

### Phase 3: First 30 Days Post-Launch

#### Content Marketing (SEO Blog Posts)

Target long-tail search queries Kenyans actually search for:

| Blog Post Title | Target Keywords |
|-----------------|----------------|
| "How to Track M-PESA Spending in 2026" | mpesa spending tracker, track mpesa |
| "Why Your M-PESA Transaction Costs Add Up" | mpesa transaction cost, mpesa charges |
| "Best Budget Apps for Kenya" | budget app kenya, finance app kenya |
| "How to Manage M-PESA Expenses" | manage mpesa expenses |
| "M-PESA Spending Categories Explained" | mpesa categories |

Each post ends with a Play Store CTA and app screenshots.

#### Video Content

| Format | Content |
|--------|---------|
| **YouTube Short / TikTok** | 60s demo: "Watch PesaTrack auto-detect an M-PESA SMS" |
| **YouTube tutorial** | "How I track every shilling I spend with PesaTrack" |
| **Kenyan tech YouTubers** | Reach out for free review — they need content, you need exposure |

#### In-App Growth Mechanics

- [ ] **Share button in Settings** — one-tap WhatsApp share of Play Store link
- [ ] **"Rate us" prompt** — trigger after user categorizes ≥20 expenses (engaged users give better ratings)
- [ ] **Never buy reviews** — Google detects and penalizes this

### Phase 4: Ongoing (Month 2+)

#### Community Partnerships

| Partner Type | Approach |
|-------------|----------|
| **Personal finance bloggers** (Kenya) | Free early access → review post |
| **Financial literacy NGOs** | PesaTrack aligns with financial education missions |
| **University finance clubs** | Students are heavy M-PESA users — demo at events |
| **Chama groups** | Built-in "Chama Contributions" category shows you understand the audience |

#### Iterate on ASO

- Monitor Play Store Console analytics: search terms, conversion rates
- A/B test screenshots and descriptions
- Add more localizations (Swahili first, then French for East Africa)

---

## Growth Funnel

```
Website + Blog SEO ──┐
Social Media ────────┤
Product Hunt ────────┼──► Play Store Listing ──► Install ──► Onboarding
Tech Blog Coverage ──┤                                        │
YouTube/TikTok ──────┤                                        ▼
Word of Mouth ───────┘                                   Active Usage
                                                              │
                                                    ┌─────────┼─────────┐
                                                    ▼         ▼         ▼
                                               Rate ⭐    Share 📤   Content 📝
                                                    │         │
                                                    ▼         ▼
                                             Higher Rank   More Installs
                                                    │         │
                                                    └────┬────┘
                                                         ▼
                                                   Play Store ──► (cycle repeats)
```

---

## Timeline

| When | Action |
|------|--------|
| **Now** | Register domain; scaffold website; prepare social media drafts |
| **This week** | Build landing page + privacy policy migration; prepare Product Hunt listing |
| **Day 1 (approval)** | Post to Twitter, Reddit, Telegram, LinkedIn simultaneously |
| **Week 1** | Product Hunt launch + email 3 Kenyan tech bloggers |
| **Week 2–4** | Publish 2 SEO blog posts + 1 YouTube demo video |
| **Month 2+** | Add in-app Share/Rate prompts; continue content marketing; iterate ASO |

---

## Key Metrics to Track

| Metric | Tool | Target (Month 1) |
|--------|------|-------------------|
| Play Store impressions | Play Console | 1,000+ |
| Install conversion rate | Play Console | >30% |
| Active installs | Play Console | 100+ |
| Website visitors | Cloudflare Analytics | 500+ |
| Play Store rating | Play Console | ≥4.5 ⭐ |
| Blog post organic traffic | Cloudflare Analytics | Growing month-over-month |

---

## Budget

| Item | Cost | Frequency |
|------|------|-----------|
| Domain (`.co.ke`) | ~KES 1,000 ($8) | Annual |
| Hosting (Cloudflare Pages) | Free | — |
| SSL | Free | — |
| Product Hunt | Free | One-time |
| Social media | Free (organic) | Ongoing |
| **Total Year 1** | **~$8** | |
