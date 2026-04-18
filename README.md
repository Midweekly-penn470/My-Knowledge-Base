# 🧠 My-Knowledge-Base - Build Your Personal Q&A Hub

[![Download](https://img.shields.io/badge/Download-My--Knowledge--Base-blue?style=for-the-badge)](https://github.com/Midweekly-penn470/My-Knowledge-Base)

## 🚀 What This App Does

My-Knowledge-Base is a local AI knowledge base app for Windows.

It lets you:

- Upload documents
- Extract text with OCR
- Store files and data in a clean workspace
- Ask questions from your own content
- See where each answer came from

It is built with Spring Boot, React, Vite, Dify, PostgreSQL, MinIO, and SSE.

## 📥 Download

Visit this page to download and use the app:

[https://github.com/Midweekly-penn470/My-Knowledge-Base](https://github.com/Midweekly-penn470/My-Knowledge-Base)

Open the page, find the latest release or package, then download the Windows file if one is provided.

## 🪟 Windows Requirements

Before you run the app, make sure your PC has:

- Windows 10 or Windows 11
- At least 8 GB of RAM
- 10 GB of free disk space
- A stable internet connection for the first setup
- Permission to run downloaded apps

For best results, close other large apps before you start.

## 🧩 What You Need First

This app works best when these services are available:

- PostgreSQL for data storage
- MinIO for file storage
- Dify for AI chat and knowledge retrieval
- OCR support for scanned files and images

If the download includes a bundled version, you can start the app with a few clicks. If it includes service files, keep them in one folder and follow the order in the setup steps below.

## 🛠️ How to Install on Windows

1. Open the download page:
   [https://github.com/Midweekly-penn470/My-Knowledge-Base](https://github.com/Midweekly-penn470/My-Knowledge-Base)

2. Download the Windows release file or package.

3. If the file is in a ZIP archive, right-click it and choose **Extract All**.

4. Move the extracted folder to a simple path such as:
   `C:\My-Knowledge-Base`

5. Open the folder and look for a file named like one of these:
   - `start.bat`
   - `run.bat`
   - `My-Knowledge-Base.exe`
   - `app.exe`

6. Double-click the file to start the app.

7. If Windows asks for permission, choose **Yes** or **Run anyway**.

8. Wait for the app to finish loading. The first start may take a short time.

## 🔧 First-Time Setup

When the app opens for the first time, set up these items:

- Local database connection
- File storage folder
- OCR path or OCR option
- Dify API address and key, if required
- Admin account or login info, if shown

Use the default values if the package already includes them. If the app asks for a local address, open the one shown in the window or browser.

## 📂 Uploading Documents

You can add files to your knowledge base with common document types such as:

- PDF
- Word files
- Text files
- Images
- Scanned pages

To upload a file:

1. Open the upload page
2. Choose one or more files
3. Start the upload
4. Wait for the import to finish
5. Check the status before asking questions

Large files may take longer. Scanned pages may also need OCR before the text becomes searchable.

## 🔍 OCR for Scanned Files

OCR reads text from images and scanned documents.

Use OCR when your file:

- Has no selectable text
- Came from a scanner
- Is a photo of a page
- Has tables or forms that need text extraction

After OCR runs, the app stores the text in the knowledge base so you can search and ask questions from it.

## 💬 Asking Questions

After your files are added, you can ask questions in plain language.

Examples:

- What does this document say about payments?
- Show me the section about account setup
- Which file mentions renewal dates?
- Summarize the policy in simple terms

The app will return an answer and show the source content used to create it. That helps you check the result against the original file.

## 🧭 Source-Based Answers

This app supports source-based Q&A.

That means it can:

- Find the matching text in your uploaded files
- Use that text to form an answer
- Show where the answer came from

This is useful when you need proof for a reply or want to check the source before you trust the answer.

## 🗂️ Common Folder Layout

A typical Windows setup may use a folder like this:

- `app` for the main program
- `data` for local data
- `uploads` for files you add
- `logs` for error logs
- `config` for settings

Keep the folder together and do not move single files out of it unless the setup guide says to do so.

## 🧪 If the App Does Not Open

Try these steps:

1. Right-click the app file and choose **Run as administrator**
2. Check whether Windows blocked the file
3. Make sure the ZIP file was fully extracted
4. Confirm the folder path does not contain special symbols
5. Restart your PC and try again
6. Check the `logs` folder for a clear error message

If the app opens in a browser but shows a blank page, refresh once and wait for a few seconds.

## 🔗 Main Project Link

[My-Knowledge-Base on GitHub](https://github.com/Midweekly-penn470/My-Knowledge-Base)

## 🧾 Useful Topics Covered

This project includes common tools and ideas for:

- AI knowledge base
- Document search
- OCR text capture
- PostgreSQL storage
- MinIO file handling
- React UI
- Vite frontend
- Spring Boot backend
- SSE live response updates
- Dify integration
- RAG-based question answering

## 🔒 Privacy and Local Use

If you run the app on your own PC, your files stay on your machine unless you connect external services.

This setup is useful when you want:

- A private document search tool
- A personal file Q&A system
- A local workspace for team notes
- A simple way to search long documents

## 📁 Supported Use Cases

You can use My-Knowledge-Base for:

- Company policy lookup
- Meeting notes search
- User manual search
- Contract review
- Study notes Q&A
- Scanned archive search
- Internal knowledge storage

## ⚙️ Basic Operation Flow

1. Start the app
2. Upload a document
3. Let OCR or text parsing finish
4. Save the file into the knowledge base
5. Ask a question
6. Read the answer and source

## 🖥️ If You Want a Cleaner Start

For a smooth first run:

- Use a short folder path
- Keep the file names simple
- Upload one test file first
- Check that the app opens before adding many files
- Save your Dify and database settings in one place

## 📦 File Types That Work Well

The best results usually come from:

- PDF manuals
- Text reports
- Meeting transcripts
- Scanned forms
- Notes in plain text
- Images with clear text

Clear files give better OCR results and better answers.

## 🛟 Helpful Tips

- Use small test files first
- Keep backups of important documents
- Use source links to verify answers
- Split large scans into smaller files
- Recheck OCR text when the source is blurry

## 📬 Start Here

[Download or open the project page](https://github.com/Midweekly-penn470/My-Knowledge-Base)

