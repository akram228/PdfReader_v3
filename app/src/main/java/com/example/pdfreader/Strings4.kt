package com.example.pdfreader

/*  Order: English, Myanmar, Arabic, Urdu, Hindi, Chinese, Japanese, Korean, Thai  */
internal val STR4: Map<String, Array<String>> = mapOf(
    "opening_pdf" to arrayOf(
        "Loading PDF…", "PDF ကို ဖွင့်နေသည်…", "جارٍ فتح PDF…", "PDF کھولا جا رہا ہے…",
        "PDF खोला जा रहा है…", "正在打开 PDF…", "PDFを開いています…", "PDF 여는 중…", "กำลังเปิด PDF…"
    ),
    "form_fill" to arrayOf(
        "Fill form", "ဖောင် ဖြည့်မည်", "تعبئة النموذج", "فارم بھریں",
        "फ़ॉर्म भरें", "填写表单", "フォームに入力", "양식 작성", "กรอกแบบฟอร์ม"
    ),
    "form_loading" to arrayOf(
        "Reading the form…", "ဖောင်ကို ဖတ်နေသည်…", "جارٍ قراءة النموذج…", "فارم پڑھا جا رہا ہے…",
        "फ़ॉर्म पढ़ा जा रहा है…", "正在读取表单…", "フォームを読み込み中…", "양식을 읽는 중…", "กำลังอ่านแบบฟอร์ม…"
    ),
    "form_none" to arrayOf(
        "This PDF has no fillable form fields.",
        "ဒီ PDF မှာ ဖြည့်လို့ရတဲ့ ဖောင်ကွက် မရှိပါ။",
        "لا يحتوي ملف PDF هذا على حقول نموذج قابلة للتعبئة.",
        "اس PDF میں بھرنے کے قابل فارم فیلڈز نہیں ہیں۔",
        "इस PDF में भरने योग्य फ़ॉर्म फ़ील्ड नहीं हैं।",
        "此 PDF 没有可填写的表单字段。",
        "このPDFには入力できるフォーム欄がありません。",
        "이 PDF에는 작성할 수 있는 양식 필드가 없습니다.",
        "PDF นี้ไม่มีช่องแบบฟอร์มที่กรอกได้"
    ),
    "form_save" to arrayOf(
        "Save filled PDF", "ဖြည့်ပြီး PDF သိမ်းမည်", "حفظ PDF المعبأ", "بھرا ہوا PDF محفوظ کریں",
        "भरा हुआ PDF सहेजें", "保存已填写的 PDF", "入力済みPDFを保存", "작성한 PDF 저장", "บันทึก PDF ที่กรอกแล้ว"
    )
)
