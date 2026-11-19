package com.example.pdfreader

/*  Order: English, Myanmar  (other languages fall back to English)  */
internal val STR5: Map<String, Array<String>> = mapOf(
    "sel_copy" to arrayOf("Copy", "ကူးယူမည်"),
    "sel_all" to arrayOf("Select all", "အားလုံးရွေးမည်"),
    "sel_rotate" to arrayOf("Rotate page", "စာမျက်နှာလှည့်မည်"),
    "sel_read" to arrayOf("Read aloud", "အသံထွက်ဖတ်မည်"),
    "sel_hl" to arrayOf("Highlight", "မှတ်မည်"),
    "x_zawgyi" to arrayOf("Zawgyi ↔ Unicode", "Zawgyi ↔ Unicode ပြောင်းမည်"),
    "zg_to_uni" to arrayOf("Zawgyi → Unicode", "Zawgyi → Unicode"),
    "zg_to_zg" to arrayOf("Unicode → Zawgyi", "Unicode → Zawgyi"),
    "zg_page" to arrayOf("Use this page's text", "ဒီစာမျက်နှာရဲ့ စာကို ယူမည်"),
    "zg_copy" to arrayOf("Copy", "ကူးယူမည်"),
    "zg_partial" to arrayOf(
        "Some rare Zawgyi letters (stacked letters) could not be converted.",
        "Zawgyi အက္ခရာ အချို့ (ထပ်ရေးအက္ခရာများ) ကို မပြောင်းနိုင်ပါ။"
    ),
    "x_rotate_page" to arrayOf("Rotate this page (view only)", "ဒီစာမျက်နှာကို လှည့်မည် (ကြည့်ရုံသာ)"),
    "page_slider" to arrayOf("Fast page scrollbar", "စာမျက်နှာ အမြန်ဆွဲ scrollbar"),
    "h_zoom" to arrayOf("Zoom for swipe pages: {0}x", "ဘေးဆွဲ စာမျက်နှာ ချဲ့ခြင်း: {0}x"),
    "split_pdf" to arrayOf("✂️  Split PDF into files", "✂️  PDF ကို ဖိုင်များ ခွဲမည်"),
    "compress_pdf" to arrayOf("🗜  Compress PDF", "🗜  PDF ကျုံ့မည်"),
    "split_every" to arrayOf("Every N pages", "စာမျက်နှာ N ခုစီ"),
    "split_ranges" to arrayOf("My own parts", "ကိုယ်ရွေးတဲ့ အပိုင်းများ"),
    "split_every_hint" to arrayOf("Pages per file, e.g. 50", "ဖိုင်တစ်ခုမှာ စာမျက်နှာ အရေအတွက် ဥပမာ 50"),
    "split_ranges_hint" to arrayOf("e.g. 1-50, 51-120, 121-", "ဥပမာ 1-50, 51-120, 121-"),
    "split_note" to arrayOf(
        "Each part becomes its own PDF in a folder you choose.",
        "အပိုင်းတစ်ခုစီ ကိုယ်ပိုင် PDF ဖိုင် ဖြစ်ပြီး ရွေးထားတဲ့ ဖိုလ်ဒါထဲ သိမ်းပါမယ်။"
    ),
    "split_btn" to arrayOf("Choose folder & split", "ဖိုလ်ဒါရွေးပြီး ခွဲမည်"),
    "split_progress" to arrayOf("Writing file {0} of {1}…", "ဖိုင် {0}/{1} ရေးနေသည်…"),
    "split_done" to arrayOf("Done: {0} files saved", "ပြီးပါပြီ: ဖိုင် {0} ခု သိမ်းပြီး"),
    "split_too_many" to arrayOf(
        "Too many files (max 200). Use bigger parts.",
        "ဖိုင်များလွန်းသည် (အများဆုံး ၂၀၀)။ အပိုင်းကို ပိုကြီးအောင် ခွဲပါ။"
    ),
    "compress_light" to arrayOf("Light (best quality)", "ပေါ့ပေါ့ (အရည်အသွေးအကောင်းဆုံး)"),
    "compress_balanced" to arrayOf("Balanced", "အလယ်အလတ်"),
    "compress_strong" to arrayOf("Strong (smallest file)", "အကြီးအကျယ် (ဖိုင်အသေးဆုံး)"),
    "compress_info" to arrayOf(
        "Only the pictures inside are made smaller. Text, links and headings stay the same. A PDF with no big pictures will not shrink much.",
        "ထဲက ပုံတွေကိုသာ သေးအောင်လုပ်ပါတယ်။ စာသား၊ link၊ ခေါင်းစဉ်တွေ မပြောင်းပါ။ ပုံကြီးမပါတဲ့ PDF က သိပ်မသေးပါ။"
    ),
    "compress_btn" to arrayOf("Compress & save", "ကျုံ့ပြီး သိမ်းမည်"),
    "compress_progress" to arrayOf("Compressing… page {0}/{1}", "ကျုံ့နေသည်… စာမျက်နှာ {0}/{1}"),
    "compress_done" to arrayOf("Smaller: {0} → {1}", "သေးသွားပါပြီ: {0} → {1}"),
    "compress_none" to arrayOf(
        "Could not make this file smaller. An unchanged copy was saved.",
        "ဒီဖိုင်ကို ပိုမသေးအောင် မလုပ်နိုင်ပါ။ မူရင်းအတိုင်း ကော်ပီ သိမ်းထားပါတယ်။"
    ),
    "zg_to_uni" to arrayOf("Zawgyi → Unicode", "Zawgyi → Unicode ပြောင်း"),
    "zg_original" to arrayOf("Original", "မူရင်း"),
    "zg_looks" to arrayOf("This looks like Zawgyi text.", "ဒီစာက Zawgyi နဲ့ ရေးထားတာ ဖြစ်နိုင်ပါတယ်။"),
    "zg_partial" to arrayOf(
        "Some rare letters (stacked Pali letters, kinzi) could not be converted.",
        "ပါဠိ အထပ်စာလုံး၊ ကင်းဆီး စတဲ့ စာလုံးအချို့ ပြောင်းမပေးနိုင်ပါ။"
    )
)
