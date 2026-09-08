---
title: "PesaTrack na SMS zako: hasa tunachosoma, hasa tusichosoma"
dek: "Muhtasari rahisi wa SMS zipi PesaTrack inaangalia, kinachotokea kwenye kifaa, na kinachotoka. Ukweli: data ya miamala haitoki."
publishedAt: 2026-09-08
tags: ["privacy"]
author: "PesaTrack"
locale: "sw"
translationOf: "pesatrack-and-your-sms"
tldr: |
  PesaTrack inaomba READ_SMS na RECEIVE_SMS. Receiver inashughulikia MPESA
  na NCBA pekee. Uchambuzi unafanyika ndani ya kifaa; kiasi, wapokeaji,
  kategoria, na maandishi kamwe havitumwi. INTERNET inatumika kwa Firebase
  Analytics ya hiari pekee (imezimwa kwa msingi) — majina ya matukio, si
  maudhui.
---

## Kwa nini tunaandika post hii

Swali maarufu zaidi kuhusu PesaTrack — kutoka waandishi wa habari, watumiaji,
na hata AI answer engines wanaotuquote — ni *"kinaifanya nini na SMS zangu?"*
Jibu liko kwenye ukurasa wa [Faragha](/sw/privacy) na ni la kuchosha, hiyo
ndiyo point. Post hii ni toleo la simulizi, kwa msomaji anayetaka kuona
sababu.

## Maswali matatu tunayoulizwa

### 1. Je, inasoma SMS zangu zote?

Hapana. Android `SmsReceiver` — class inayoendesha SMS inapofika — inaangalia
jina la mtumaji **kabla** ya mwili wa ujumbe kupelekwa kwa parser. Kama
mtumaji si `MPESA` au `NCBA`, receiver inapuuza na mwili wa ujumbe kamwe
hausomwi. SMS za matangazo, OTP kutoka kwa huduma nyingine, ujumbe kutoka
kwa marafiki — vyote vinapuuzwa hapo hapo.

Tungeweza kuifanya receiver iwe smart zaidi ("angalia mwili, kisia
mtumaji"), lakini tulichagua kutofanya. Orodha kali ya watumaji ni dhamana
ya kweli. Chochote kingine kingehitaji "lakini" ambazo hatutaki kuandika.

### 2. Je, kuna kinachotoka kwenye simu yangu?

**Data yako ya miamala** — kiasi, wapokeaji, majina ya kategoria, bajeti,
maandishi — haitoki kwenye kifaa. Iko kwenye Room database ya faragha ndani
ya sandbox ya app. Apps nyingine kwenye simu yako haziwezi kuisoma, na
hakuna kitu kwenye code kinachoitumia.

App **inashikilia** ruhusa ya `INTERNET`. Tulikuwa wazi kuihusu tulipoongeza
kwenye v1.5.0 na tutakuwa wazi hapa: ipo kwa ajili ya **Firebase Analytics
ya hiari isiyokuwa na utambulisho**. Hiyo imezimwa kwa msingi. Ikiwa
hutaiwasha, socket kamwe haifunguki. Ukiiwasha, tunachotuma ni:

- Matukio ya kufungua skrini (`"opened analytics"`, `"opened budgets"`)
- Matukio ya matumizi ya vipengele (`"import_completed"`, `"budget_created"`)
- Toleo la app, toleo la Android, aina ya simu, nchi
- Ishara za crash na hitilafu

Tusichotuma, kamwe:

- Maudhui ya SMS
- Kiasi cha miamala, wapokeaji, kodi za miamala, kategoria, maandishi
- Chochote kinachoweza kukutambulisha binafsi
- Nambari yako ya simu, nambari ya M-PESA, barua pepe, jina

Kama muafaka huo hauridhishi, acha uchambuzi umezimwa. App inafanya kazi
vilevile.

### 3. Nikifuta app?

Database iko ndani ya sandbox ya app. Android inafuta zote unapofuta app.
Hakuna profile ya server ya kufunga kwa sababu hakuna profile ya server.

## Kwa nini tuliipanga hivi

Kanuni mbili kutoka [kurasa ya kuhusu](/about) zilituelekeza:

- **Faragha si ya mazungumzo.** Hakuna kipengele kinachohitaji kutuma SMS
  ghafi au taarifa binafsi bila idhini ya wazi, inayoweza kurudi.
- **Nambari za kweli.** Hii inajumuisha ukweli kuhusu kile tunachotuma
  unapokubali.

Tungeweza kutoa toleo la "cloud sync" ambalo lingetupa uchambuzi wa kina
zaidi, ripoti bora za crash, na muunganisho wa vifaa vingi. Tulichagua
kutofanya. Iwapo tutatoa sync siku moja itakuwa bidhaa tofauti, ya hiari,
inayoweza kurudi — si sasisho la kimya.

## Njia ya ukaguzi

Ukitaka kuhakiki chochote:

- [AndroidManifest.xml](https://github.com/J-Mumo/PesaTrack) inaorodhesha kila
  ruhusa app inaomba.
- Class ya [`SmsReceiver`](https://github.com/J-Mumo/PesaTrack) ndiyo mlango
  pekee wa maudhui ya SMS.
- [Ukurasa wa Faragha](/sw/privacy) ni sera hiyo hiyo Google Play inayounganisha.
- [Factsheet](/factsheet.json) ni toleo linaloweza kusomwa na mashine.

Ukiona tofauti yoyote kati ya hivi, tunataka kusikia.
Tuma barua pepe kwa [support](/support) na "PRIVACY AUDIT" kwenye subject.
