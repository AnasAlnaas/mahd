# ماذا آكل؟ — مسودة للمراجعة

**Status: DRAFT for the owner's review. Nothing here is in the app yet, and none of it is reviewed by a doctor.**
Written 2026-10-13. Once you have edited it, tell Claude and it will be turned into `app/src/main/assets/content/what_to_eat.json`
(with `"reviewed": false`, so the app shows its draft line) and a screen.

## How to review (you can edit this file directly, or just tell Claude what to change)

- **Last column "قرارك"**: write ✔ to accept, ✘ to delete the row, or write the new wording / verdict.
- **To add a food**: copy any row, change the text, and give it a source from the list at the bottom (or write "no source yet").
- **Mother text** ("ما تقرؤه الأم") is what the app would show. It is written in simple Arabic, feminine form. Change it freely.
- Rows marked **ضعيف** (weak evidence) are there because mothers will ask about them, not because a health body settled them.
  Please show those to a doctor before they go in the app.

**Verdicts** (same colours as the rest of the app): مناسب (green) · باعتدال (amber, there is a limit) ·
بشرط (amber, only if cooked / pasteurised / washed) · تجنّبيه (red) · اسألي الطبيب (grey).
**"—" in the breastfeeding column means our sources give no special advice. It does not mean "proven safe".**
**Evidence**: **قوي** = a health body (NHS, FDA, CDC, WHO, NCCIH) says it directly · **متوسط** = a health body or review, but
I had to apply it to a Libyan food or to a slightly different case · **ضعيف** = only secondary sources.

Decisions already in `CLAUDE.md` that this draft follows: the app promotes breastfeeding only (sources' remarks about
bottles are dropped on purpose); the mother is addressed in the feminine; no questionnaires; no "call ambulance" button.

---

## قواعد عامة (نصائح تظهر في أول الشاشة)

| # | النص | المصدر · الدليل | قرارك |
|---|---|---|---|
| T1 | لا تحتاجين أن تأكلي «لاثنين»، حتى لو كان الحمل بتوأم. ونوّعي طعامك على مدار الأسبوع. | N2 · قوي | |
| T2 | اغسلي الخضار والفاكهة جيدًا، ونظّفي الأدوات وسطح المطبخ بالماء الساخن والصابون بعد كل طعام. | N2، TOX · قوي | |
| T3 | إن كان عندكم قطّوس في البيت: لا تنظّفي صندوق فضلاته بيدك، أو البسي قفازًا واغسلي يديك بعدها. (يمكن أن تنقل فضلات القطط طفيليًا اسمه التوكسوبلازما.) | TOX · قوي | |
| T4 | إن أكلتِ شيئًا من قائمة «تجنّبيه» ولم تشعري بشيء، فالكمية القليلة غالبًا لا تؤثر. راجعي الطبيب إن شعرتِ بتعب أو حرارة أو قيء أو إسهال بعده. | N1 · قوي | |
| T5 | (للأم المرضع) لا يوجد طعام ممنوع تمامًا غير ما هو مذكور في القائمة. إن لاحظتِ أن طفلك يتأثر بشيء تأكلينه فاسألي الطبيب. | N4 · قوي | |

---

## 1. اللحوم والبيض

| # | الطعام | حمل | رضاعة | ما تقرؤه الأم | المصدر · الدليل | قرارك |
|---|---|---|---|---|---|---|
| 1 | اللحم والدجاج المطبوخان جيدًا (غنم، بقر، دجاج) | مناسب | — | مصدر جيد للبروتين. اطبخيه حتى لا يبقى أثر للون الوردي أو الدم. | N1، N2، TOX · قوي | |
| 2 | لحم الجمل (محلي) | بشرط | — | كُليه مطبوخًا جيدًا فقط. لا تأكليه نيئًا أو ناقص النضج. | N1، BR1 · قوي | |
| 3 | اللحم المفروم والكفتة والمقانق والمرقاز (محلي) | بشرط | — | تأكدي أنها مطبوخة من الداخل ولا أثر للون الوردي. | N1، TOX · قوي | |
| 4 | اللحم النيء أو ناقص النضج (شواء لم ينضج، لحم مفروم نيء) | تجنّبيه | — | قد ينقل عدوى تضرّ الحمل، منها التوكسوبلازما والبروسيلا. | N1، TOX، BR1 · قوي | |
| 5 | الكبدة وما فيه كبدة: قلاية الكبدة، والعصبان إن كان محشوًّا بالكبدة (محلي) | تجنّبيه | — | الكبدة غنية جدًا بفيتامين أ، وهذا قد يضرّ الجنين. | N1 · قوي · [م2] | |
| 6 | القديد (محلي) | بشرط | — | لا تأكليه كما هو. اطبخيه جيدًا حتى يخرج منه البخار. | N1، LIS · متوسط · [م3] | |
| 7 | اللحوم الباردة الجاهزة (مرتديلا، لانشون، هوت دوغ) | بشرط | — | سخّنيها حتى يخرج منها البخار قبل أكلها. | LIS، N1 · متوسط · [م4] | |
| 8 | لحم الصيد (الحجل، الإوز، لحم الغزال) | تجنّبيه | — | قد يحتوي على الرصاص، وهو يضرّ الجنين. | N1 · قوي · [م5] | |
| 9 | الباتيه ومعجون اللحم المبرّد | تجنّبيه | — | إلا المعلّب المغلق غير المبرّد. | LIS، N1 · قوي | |
| 10 | البيض المطبوخ جيدًا (مسلوق، مقلي متماسك) | مناسب | — | اتركيه حتى يتماسك البياض والصفار. | N2 · قوي | |
| 11 | البيض النيء أو نصف المطبوخ (مايونيز بيتي، بيض عيون سائل، موس) | تجنّبيه | — | قد يسبب تسممًا غذائيًا بالسالمونيلا. | N1، N2 · قوي · [م6] | |
| 12 | الشكشوكة والبوريك بالبيض (محلي) | بشرط | — | تأكدي أن البيض متماسك تمامًا، ولا شيء سائل فيه. | N1 · متوسط | |

## 2. الأسماك والمأكولات البحرية

| # | الطعام | حمل | رضاعة | ما تقرؤه الأم | المصدر · الدليل | قرارك |
|---|---|---|---|---|---|---|
| 13 | الأسماك قليلة الزئبق المطبوخة: سردين، بوري، جمبري، حبّار، سلمون، بلطي | مناسب | مناسب | من حصتين إلى ثلاث حصص في الأسبوع (الحصة بحجم كفّ يدك). | FDA، N2، N4 · قوي · [م7] | |
| 14 | السمك الدهني: سردين، سلمون، ماكريل | باعتدال | باعتدال | لا يزيد عن حصتين في الأسبوع بسبب الملوّثات التي تتجمّع فيه. | N1، N4 · قوي | |
| 15 | التونة المعلبة (محلي: شائعة جدًا) | باعتدال | — | لا تزيدي عن 4 علب في الأسبوع. الأفضل التونة الخفيفة (لايت). وتذكري أنها داخل البوريك والسندويتشات والسلطات أيضًا. | N1، FDA · قوي · [م8] | |
| 16 | أسماك الزئبق العالي: القرش، أبو سيف، المارلين، الماكريل الملكي، التونة كبيرة العين | تجنّبيه | باعتدال | فيها أعلى نسبة زئبق. أثناء الرضاعة: حصة واحدة في الأسبوع كحد أقصى من القرش وأبو سيف والمارلين. | N1، FDA، N4 · قوي | |
| 17 | السمك والمحار النيء (سوشي، محار) | تجنّبيه | — | قد يحمل بكتيريا أو سمومًا. | N1، TOX · قوي | |
| 18 | السمك المدخّن المبرّد | تجنّبيه | — | إلا إذا طُبخ في طبق ساخن، أو كان معلّبًا مغلقًا غير مبرّد. | LIS، N1 · قوي | |
| 19 | الحريمة وأطباق السمك المطبوخة (محلي) | مناسب | — | مناسبة إذا كان السمك مطبوخًا جيدًا ومن الأنواع قليلة الزئبق. اسألي عن نوع السمك. | FDA · متوسط | |

## 3. الألبان والأجبان

| # | الطعام | حمل | رضاعة | ما تقرؤه الأم | المصدر · الدليل | قرارك |
|---|---|---|---|---|---|---|
| 20 | الحليب واللبن والزبادي المبسترة | مناسب | — | مصدر للكالسيوم. اختاري المبستر. | N2 · قوي | |
| 21 | الحليب الخام واللبن الرائب والزبدة البلدية (بقر، غنم، ماعز) (محلي) | تجنّبيه | — | قد تحمل بكتيريا الليستيريا والبروسيلا. اختاري المبستر فقط. | N1، LIS، BR2 · قوي · [م9] | |
| 22 | حليب الناقة الطازج (محلي) | تجنّبيه | — | الحليب غير المبستر من أي حيوان قد ينقل عدوى. اختاري المبستر. | LIS، BR1، BR2 · متوسط · [م10] | |
| 23 | الجبن الأبيض الطازج والجبن البلدي (محلي) | بشرط | — | إن كان مبسترًا فلا بأس. وإلا سخّنيه حتى يخرج منه البخار قبل أكله. | LIS · متوسط | |
| 24 | الجبن المبستر: مثلثات، شيدر، موزاريلا، قريش، فيتا | مناسب | — | تأكدي أن العبوة مكتوب عليها «مبستر». | LIS · قوي | |
| 25 | الجبن الأزرق وجبن القشرة البيضاء (روكفور، بري، كامومبير) | بشرط | — | اطبخيه حتى يخرج منه البخار. | N1 · قوي | |

## 4. الخضار والفاكهة

| # | الطعام | حمل | رضاعة | ما تقرؤه الأم | المصدر · الدليل | قرارك |
|---|---|---|---|---|---|---|
| 26 | الخضار والفاكهة المغسولة | مناسب | مناسب | خمس حصص في اليوم على الأقل. اغسليها جيدًا. | N2، N4 · قوي | |
| 27 | السلطة والخضرة الورقية: جرجير، بقدونس، نعناع طازج | بشرط | — | اغسليها جيدًا لإزالة التراب، فهو قد يحمل بكتيريا. | N1، TOX · قوي | |
| 28 | الدلّاع والبطيخ والشمام المقطّع (محلي) | بشرط | — | كُليه بعد تقطيعه مباشرة. لا تتركيه خارج الثلاجة أكثر من ساعتين (ساعة إن كان الجو حارًّا جدًا). | LIS · قوي | |
| 29 | البراعم النيئة | بشرط | — | اطبخيها جيدًا قبل أكلها. | LIS · قوي | |
| 30 | التمر (محلي) | مناسب | مناسب | من الفاكهة المجففة. كُليه باعتدال. وبعد الولادة تشير دراسات إلى أنه قد يزيد الحليب. | N2، DATE2 · متوسط | |
| 31 | التمر في الأسابيع الأخيرة من الحمل (محلي) | اسألي الطبيب | — | تشير دراسات صغيرة إلى أنه قد يساعد على تقدّم المخاض، لكن الأدلة غير قاطعة. اسألي طبيبك عن الكمية، خاصة إن كان عندك سكري حمل. | DATE1، DATE2 · متوسط · [م11] | |
| 32 | العصائر الطبيعية | مناسب | — | تُحسب ضمن حصص الفاكهة. اغسلي الفاكهة قبل عصرها. | N2 · متوسط | |

## 5. النشويات والبقوليات والأطباق الليبية

| # | الطعام | حمل | رضاعة | ما تقرؤه الأم | المصدر · الدليل | قرارك |
|---|---|---|---|---|---|---|
| 33 | البازين (محلي) | مناسب | — | غذاء مشبع من دقيق الشعير. كُليه مع الخضار واللحم المطبوخ جيدًا. | N2، WIKI · متوسط | |
| 34 | الكسكسي والرشتة والإمبكبكة والمعكرونة (محلي) | مناسب | — | نشويات جيدة. أكثري من الخضار معها. | N2، WIKI · متوسط | |
| 35 | الخبز: الطنّور، والأفضل خبز الحبوب الكاملة | مناسب | — | النشويات تكون أكثر من ثلث طعامك، والأفضل الكاملة. | N2 · قوي | |
| 36 | الفول والحمص والعدس والفاصولية | مناسب | — | بروتين وألياف. | N2 · قوي | |
| 37 | الشربة والبرودو (حساء الخضار) (محلي) | مناسب | — | خيار جيد. احرصي أن يكون اللحم فيها مطبوخًا جيدًا. | N2، WIKI · متوسط | |
| 38 | المبطّن والبوريك المقليّان (محلي) | باعتدال | — | لذيذان لكنهما مقليّان ودسمان. تأكدي من نضج الحشوة والبيض. | N2، N1 · متوسط · [م12] | |
| 39 | العصيدة بالرُّب أو العسل أو السمن (محلي) | باعتدال | — | غنية بالسكر والدهن. كُليها باعتدال. | N2، WIKI · متوسط | |
| 40 | الحلويات: مقروض، غريبة، بسبوسة، بقلاوة، بسيسة | باعتدال | — | كُليها باعتدال، فهي غنية بالسكر والدهن. | N2، WIKI · قوي | |
| 41 | قمر الدين والمشروبات المحلّاة (محلي) | باعتدال | — | فيها سكر مضاف. اشربيها باعتدال. | N2، WIKI · متوسط | |
| 42 | الفول السوداني والمكسرات | مناسب | مناسب | آمنة ما لم تكوني تعانين من حساسية. | N1، N4 · قوي | |
| 43 | الشوكولاتة | باعتدال | باعتدال | فيها قليل من الكافيين. احسبيه ضمن حصتك اليومية. | N1 · قوي | |

## 6. المشروبات

| # | الطعام | حمل | رضاعة | ما تقرؤه الأم | المصدر · الدليل | قرارك |
|---|---|---|---|---|---|---|
| 44 | الماء | مناسب | مناسب | اشربيه بانتظام. وإن كنتِ تشعرين بالغثيان فاشربيه قليلًا قليلًا. | N3 · متوسط | |
| 45 | الماء غير المعالج (آبار أو خزانات غير معقّمة) | تجنّبيه | — | تجنّبي شرب الماء غير المعالج. | TOX · قوي | |
| 46 | القهوة (عربية، نسكافيه، إسبريسو) | باعتدال | باعتدال | في الحمل: لا تزيدي عن 200 ملغ كافيين في اليوم (فنجان نسكافيه نحو 100 ملغ). في الرضاعة: لا تزيدي عن 300 ملغ، والأفضل تقليله لأنه قد يجعل طفلك متوترًا. | N1، ACOG، N4 · قوي · [م13] | |
| 47 | الشاي: الأحمر الليبي، الأخضر، بالنعناع (محلي) | باعتدال | باعتدال | كوب الشاي نحو 75 ملغ كافيين. احسبي الشاي والقهوة والكولا معًا حتى لا تتجاوزي حدّك اليومي. | N1، N4 · قوي · [م13] | |
| 48 | الكولا ومشروبات الطاقة | باعتدال | باعتدال | علبة الكولا نحو 40 ملغ كافيين، وعلبة الطاقة (250 مل) نحو 80 ملغ، وفيهما سكر كثير. | N1، N2 · قوي · [م14] | |
| 49 | شاي الأعشاب: النعناع والبابونج | باعتدال | — | لا يزيد عن 4 أكواب في اليوم، لأن الدراسات عليه في الحمل قليلة. | N1 · قوي | |
| 50 | عرق السوس ومشروبه (محلي) | تجنّبيه | — | الأعشاب التي فيها عرق السوس قد تضرّ نمو الجنين. | N1 · قوي | |
| 51 | الزنجبيل | مناسب | — | في الطعام والشراب قد يخفف الغثيان. أما المكمّلات فاسألي الصيدلي أولًا. | N3 · قوي | |
| 52 | الحلبة (محلي) | بشرط | اسألي الطبيب | في الطعام بكميات قليلة لا بأس. أما الحلبة المنقوعة بكثرة والكبسولات فغير آمنة في الحمل. وفي الرضاعة: الدراسات متضاربة في زيادتها للحليب. | NCCIH · قوي | |
| 53 | الشيح والمريمية (محلي) | تجنّبيه | — | فيهما مادة الثوجون. يُنصح بتجنّبهما احتياطًا أثناء الحمل. | HERB · ضعيف · [م15] | |
| 54 | حبة البركة (الحبة السوداء) | اسألي الطبيب | — | لا توجد دراسات كافية عن سلامتها بكميات كبيرة في الحمل. اسألي طبيبك. | HERB · ضعيف · [م16] | |
| 55 | الكركديه (محلي) | اسألي الطبيب | — | لا توجد دراسات كافية عن سلامته في الحمل. اسألي طبيبك. | HERB · ضعيف | |
| 56 | التوابل بكميات الطبخ المعتادة: هريسة، بزار، تابل، كمون، كزبرة، قرفة، كركم، ثوم (محلي) | مناسب | — | الأعشاب والتوابل المستعملة في الطبخ بكميات معتدلة لا يُتوقع أن تسبب مشكلة. | MTB · متوسط · [م17] | |
| 57 | وصفات العطّار والخلطات العشبية والكبسولات والزيوت العطرية | اسألي الطبيب | اسألي الطبيب | لا تتناوليها قبل أن تسألي الطبيب أو الصيدلي، فكثير منها لم يُدرس في الحمل والرضاعة. | MTB · قوي | |
| 58 | المشروبات الكحولية | تجنّبيه | تجنّبيه | تضرّ الجنين ضررًا قد يدوم. وفي الرضاعة الأسلم تركها. | N1، N4 · قوي · [م18] | |

## 7. الجاهز والمعبّأ

| # | الطعام | حمل | رضاعة | ما تقرؤه الأم | المصدر · الدليل | قرارك |
|---|---|---|---|---|---|---|
| 59 | السلطات والسندويتشات الجاهزة المبرّدة (تونة، دجاج، مايونيز) | بشرط | — | الأفضل المنزلية. وإن اشتريتِ جاهزة فاحفظيها باردة وكُليها قبل تاريخ الانتهاء. | LIS، N1 · قوي · [م19] | |

---

## ملاحظات للمراجِع (owner / doctor)

- Row numbers are only for this review; the app will use stable ids, assigned after you approve.
- **[م2]** Libyan الكبدة / العصبان: the filling of العصبان includes organs (per the Wikipedia article on Libyan cuisine). Please check what
  your family's recipe uses, and whether the row should name it at all.
- **[م3]** No source names القديد. I applied the "cured / dried meat: heat until steaming hot" rule of NHS and CDC by analogy. Needs a doctor.
- **[م4]** NHS allows cold sliced ham if kept chilled; CDC says heat deli meats until steaming hot. The draft follows CDC (stricter).
- **[م5]** Game meat is probably rare for the app's users. Delete the row if you prefer.
- **[م6]** The NHS says raw eggs are fine only if they carry the British Lion stamp. Libya has no such stamp, so the draft says "fully cooked only".
- **[م7]** NHS: 2 portions of fish a week. FDA: 2 to 3 servings of "best choice" fish. The draft says "2 to 3". You can choose one.
- **[م8]** NHS: up to 4 cans of tuna a week. FDA: canned light tuna 2 to 3 servings a week, but white (albacore) only 1. The draft uses the NHS number
  and prefers light tuna. Tuna is very common in Libya (bureek, sandwiches, salads), so this row may matter most in this section.
- **[م9]** Many families boil raw milk. Our sources say "avoid unpasteurised" and do not discuss boiled milk, so the draft does not say it is fine.
  Ask a doctor whether the app may say "boiled raw milk is acceptable".
- **[م10]** The CDC and WHO pages I read name goat, sheep, cow and camel meat, and raw milk in general. They do not name camel milk
  specifically. The draft applies the general raw-milk rule.
- **[م11]** The trials behind this are small and give mixed results, and the ones I saw did not report baby outcomes. I could not read which
  week the trials started, so the draft gives no week or number of dates. A doctor should decide what the app may say.
- **[م12]** The description of المبطّن and البوريك comes from general knowledge, not from a source. Check the local recipes.
- **[م13]** Pregnancy limit 200 mg (NHS, ACOG). Breastfeeding limit 300 mg (NHS). One NHS Wales page says 200 mg for both. The small, strong
  Libyan tea glasses have no published caffeine figure, so the draft tells the mother to count everything together.
- **[م14]** Neither NHS page says energy drinks are forbidden, only that they contain caffeine. You may want to be stricter.
- **[م15]** Only secondary sources (clinic articles, a university course sheet, a university herb list) warn about الشيح and المريمية in pregnancy
  (the compound thujone). No major health body page was found. The draft says "avoid as a precaution".
- **[م16]** حبة البركة is also a religious and cultural topic. The warnings I found are weak (lists quoting one clinician). The draft only says
  "ask your doctor". Please decide how you want this worded.
- **[م17]** Source is a summary of MotherToBaby's general herbal page (I did not read the full text).
- **[م18]** Alcohol may not need to appear in an app for Muslim families at all. Your call. For breastfeeding the NHS allows an occasional small drink;
  the draft does not say that.
- **[م19]** NHS: keep chilled and eat before the use-by date. CDC: avoid store-bought deli salads, homemade is fine. The draft keeps both ideas.

## أسئلة مفتوحة (no good source found yet: for you or a doctor)

1. **العسل**: no source read. Mothers will ask.
2. **الأكل الحار (هريسة، فلفل)** and heartburn: no health-body statement found.
3. **Iron-rich foods**: liver is on the avoid list, so what does the app suggest for anaemia? (needs a doctor, and the owner's choice of sources)
4. **سكري الحمل**: what to say about التمر, الحلويات, الرُّب and white bread.
5. **لقبي** (palm sap drink in the Wikipedia article): fresh sap ferments, but I found no source on Libyan lagbi's alcohol content.
6. **الأناناس والبابايا الفجّة** and other popular myths: no source read.
7. **المحلّيات الصناعية** (سكرين، ستيفيا): no source read.
8. **رمضان**: what to say about eating and fasting for pregnant and breastfeeding mothers (separate topic; a doctor should decide).
9. Should this section be shown to a father (`Prefs.parent`)? Draft text is written to the mother. The father could see it unchanged, hidden, or in a "how to help her" version.

## المصادر

"Read" = I read the page text. "Summary" = I only saw a search summary, so please open it before relying on the exact words.

| المفتاح | المصدر | الرابط | ما قرأته |
|---|---|---|---|
| N1 | NHS: Foods to avoid in pregnancy | https://www.nhs.uk/pregnancy/keeping-well/foods-to-avoid/ | Read |
| N2 | NHS: Have a healthy diet in pregnancy | https://www.nhs.uk/pregnancy/keeping-well/have-a-healthy-diet/ | Read |
| N3 | NHS: Vomiting and morning sickness | https://www.nhs.uk/pregnancy/related-conditions/common-symptoms/vomiting-and-morning-sickness/ | Read |
| N4 | NHS: Food and drinks to avoid when breastfeeding | https://www.nhs.uk/start-for-life/baby/feeding-your-baby/breastfeeding/healthy-diet-when-breastfeeding/food-and-drinks-to-avoid-when-breastfeeding/ | Read |
| FDA | FDA/EPA: Advice about eating fish | https://www.fda.gov/food/consumers/advice-about-eating-fish | Read |
| LIS | CDC: Listeria prevention | https://www.cdc.gov/listeria/prevention/index.html | Read |
| TOX | CDC: Toxoplasmosis prevention | https://www.cdc.gov/toxoplasmosis/prevention/index.html | Read |
| BR1 | CDC: About brucellosis | https://www.cdc.gov/brucellosis/about/index.html | Read |
| BR2 | WHO: Brucellosis fact sheet | https://www.who.int/news-room/fact-sheets/detail/brucellosis | Read |
| ACOG | ACOG Committee Opinion 462: Moderate caffeine consumption during pregnancy | https://pubmed.ncbi.nlm.nih.gov/20664420/ | Summary (abstract via search) |
| NCCIH | NCCIH: Fenugreek | https://nccih.nih.gov/health/fenugreek | Read |
| MTB | MotherToBaby: Herbal products and pregnancy | https://mothertobaby.org/fact-sheets/herbal-products-pregnancy/ | Summary |
| DATE1 | Date fruit and labour outcomes, systematic review (2020) | https://pubmed.ncbi.nlm.nih.gov/32290818/ | Summary |
| DATE2 | Dates in the peripartum period, dose-response meta-analysis (2024) | https://pmc.ncbi.nlm.nih.gov/articles/PMC10759543 | Summary |
| WIKI | Wikipedia: Libyan cuisine (used only for dish and ingredient names, never for medical advice) | https://en.wikipedia.org/wiki/Libyan_cuisine | Read (first 100,000 characters) |
| HERB | Secondary sources on sage, wormwood, black seed, hibiscus (clinic articles, university course sheets, herb lists) | found by web search | Summary; weak |
