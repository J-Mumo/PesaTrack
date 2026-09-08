---
title: "Ada zako za M-PESA zinastahili safu yao wenyewe"
dek: "Ada za miamala ni pesa halisi. Hii ndiyo picha ya mwaka mzima — na kwa nini PesaTrack inaziweka peke yake."
publishedAt: 2026-09-08
tags: ["awareness"]
author: "PesaTrack"
locale: "sw"
translationOf: "mpesa-fees-deserve-own-line"
tldr: |
  Ada za miamala ya M-PESA ni pesa inayoondoka kwenye akaunti yako. PesaTrack
  inazifuatilia kwenye kategoria 606 ili uone jumla ya mwaka. Mtumiaji wa
  kawaida wa Kenya anafikia KES 8,000 hadi 15,000 kwa mwaka kwa ada. Nambari
  kwenye skrini za Takwimu zinaonyesha — na mawazo yote wazi, bila ushauri.
---

## KES 22 zisizoonekana

Unatuma KES 4,000 kwa rafiki. SMS mbili zinafika: moja ya kutuma, moja ya
`transaction cost`. Shilingi ishirini na mbili. Si jambo kubwa.

Ila nambari hiyo inatokea kila kutuma, kila till, kila kutoa, kila kuweka
M-Shwari. Ongeza wapokeaji unaowashughulikia mara chache kwa wiki — mwenye
nyumba, kituo cha matatu, mama yako — na ada zinajilundika kimya kimya.
Mtumiaji tuliyeongea naye wakati wa majaribio alipata KES 11,400 za ada
katika miezi kumi na miwili iliyopita. Si kwa sababu alikuwa mzembe. Kwa
sababu nambari haijaonyeshwa kama kitu kimoja.

Hilo ndilo tatizo ambalo PesaTrack ilijengwa kwa ajili yake.

## Tunavyofanya na ada

Kila parser — M-PESA, NCBA — inatoa ada ya muamala kutoka kwenye SMS na
inaihifadhi kama **matumizi tofauti** kwenye kategoria `606`. Si kuongeza
kwenye muamala mama. Si kuongeza kimya kwenye Usafiri au Vyakula. Safu yake
mwenyewe, tarehe yake mwenyewe, mpokeaji wake mwenyewe (huduma ya pesa ya
simu).

```text
Sept 3   Kutuma Pesa — Wanjiku      KES 4,000    (Usafiri? / Binafsi? — wewe amua)
Sept 3   Ada ya muamala             KES    22    (Kategoria 606)
```

Matokeo mawili:

1. **Jumla za kategoria kwenye Takwimu ni za kweli.** "Usafiri" haijifichi
   KES 200 za ada ambazo hukuziona.
2. **Unaweza kuona ada kama safu moja.** Takwimu → Kategoria → 606 →
   *KES 11,400 katika miezi 12*. Nambari hiyo sasa iko wazi kwa uamuzi.

## Kielelezo

Hivi ndivyo hesabu ya "hii ingekuwa nini" inavyofanya kazi. Ni kielelezo, si
pendekezo, na mawazo yote yako wazi kwenye ukurasa.

- **Kiasi:** KES 11,400 (ada za mwaka, kudhania)
- **Kiwango:** 10% kwa mwaka, kutungwa kila mwezi
- **Muda:** miaka 5
- **Deposit ya mara moja** (si mchango wa kila mwaka)

`FV = 11400 × (1 + 0.10 / 12) ^ (12 × 5) ≈ KES 18,748`

Kama badala yake kiasi kile kile kingeingia kila mwaka kwa miaka 5, kama
deposit tano tofauti za mwaka mmoja mmoja:

`≈ KES 76,000 jumla ya FV` (kadirio la ukubwa — nambari halisi inategemea
lini ndani ya mwaka ada inavyotokea)

Hatusemi utapata 10%. Hatusemi hii ina uhakika. Tunasema kwamba isipokuwa
nambari ionekane, huwezi kufanya uamuzi.

## Tusichofanya na nambari hii

- Hatutajenga "streak" inayotoa zawadi kwa mwezi wa ada chache. Ada zinaweza
  kupanda kwa sababu mapato yako yalipanda; kusherehekea wiki ya ada chache
  kunaadhibu mzunguko wa kawaida wa pesa.
- Hatutatuma tangazo mara ada zako zinapofikia kizingiti. Uelewa wa kweli ni
  muhtasari wa mwezi, si kipimo cha wakati halisi.
- Hatutapendekeza huduma mbadala. Ukiamua kutuma benki, kulipia ndani ya
  app, au kutumia cash kwa kategoria za ada juu ni bora zaidi kwako, sawa —
  huo ni uamuzi wako, umefanywa kutoka kwa data.

## Kinachoweza kufuata

Vitu viwili kwenye [roadmap](/roadmap):

- Muhtasari wa ada kwa aina ya malipo ("ada zako za Kutuma Pesa ni 60% ya jumla").
- Muonekano wa kategoria ulioanzishwa kwa ada — "Usafiri, jumla, ilikuwa KES 8,200 si KES 8,000".

Kama una upendeleo kati ya hivi viwili, tuambie kwenye
[support](/support). Tunasoma kila ujumbe.
