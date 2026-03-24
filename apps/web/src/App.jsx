import { useEffect, useMemo, useRef, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";
const AUTH_STORAGE_KEY = "mykb.auth";
const ACTIVE_TASK_STATUSES = new Set(["PENDING", "RUNNING"]);

const STATUS_LABELS = {
  PENDING: "Pending",
  RUNNING: "Running",
  SUCCEEDED: "Completed",
  FAILED: "Failed"
};

const STAGE_LABELS = {
  QUEUED: "Queued",
  OCR: "OCR",
  DIFY_UPLOAD: "Dify Upload",
  INDEXING: "Indexing",
  COMPLETED: "Completed",
  FAILED: "Failed"
};

const EMPTY_AUTH_FORM = { username: "", identity: "", password: "" };
const EMPTY_KB_FORM = { name: "", description: "" };

function readAuth() {
  try {
    const raw = window.localStorage.getItem(AUTH_STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function writeAuth(auth) {
  if (auth) {
    window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth));
  } else {
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
  }
}

function parseJson(text) {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function getErrorMessage(text, payload) {
  return payload?.message ?? payload?.code ?? text?.trim() ?? "Request failed";
}

function formatDate(value) {
  if (!value) return "n/a";
  return new Date(value).toLocaleDateString("en-US", {
    month: "short",
    day: "2-digit",
    year: "numeric"
  });
}

function formatDateTime(value) {
  if (!value) return "n/a";
  return new Date(value).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function formatBytes(size) {
  if (!Number.isFinite(size) || size <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  let value = size;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function parseEventBlock(block) {
  let event = "message";
  const dataLines = [];
  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    if (line.startsWith("data:")) dataLines.push(line.slice(5).trim());
  }
  const rawPayload = dataLines.join("\n");
  return { event, payload: parseJson(rawPayload) ?? rawPayload };
}

function makeId() {
  return globalThis.crypto?.randomUUID?.() ?? `id-${Date.now()}-${Math.random()}`;
}

function toneClass(tone) {
  if (tone === "success") return "success";
  if (tone === "danger") return "danger";
  return "neutral";
}

function detectFileType(contentType, filename) {
  const lower = (filename ?? "").toLowerCase();
  if (lower.endsWith(".pdf") || contentType?.includes("pdf")) return "pdf";
  if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "sheet";
  if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "word";
  return "text";
}

function fileIcon(type) {
  if (type === "pdf") return "picture_as_pdf";
  if (type === "sheet") return "table_chart";
  return "description";
}

function EmptyState({ icon, title, description, action }) {
  return (
    <div className="empty-state">
      <div className="empty-state-icon">
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="empty-state-copy">
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
      {action ? <div className="empty-state-action">{action}</div> : null}
    </div>
  );
}

function LoadingState({ title = "Loading", lines = 3 }) {
  return (
    <div className="loading-state" aria-label={title}>
      <div className="skeleton skeleton-title" />
      {Array.from({ length: lines }).map((_, index) => (
        <div className="skeleton skeleton-line" key={`${title}-${index}`} />
      ))}
    </div>
  );
}
async function api(path, { token, formData, ...options } = {}) {
  const headers = new Headers(options.headers ?? {});
  if (!formData) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
    body: formData ?? options.body
  });

  const text = await response.text();
  const payload = parseJson(text);
  if (!response.ok) throw new Error(getErrorMessage(text, payload));
  return payload;
}

function TopNav({ topView, setTopView, setShowCreateModal, onLogout, username }) {
  const items = [
    { key: "dashboard", label: "Dashboard" },
    { key: "workspace", label: "Workspace" },
    { key: "library", label: "Library" }
  ];

  return (
    <header className="top-nav">
      <div className="nav-left">
        <span className="brand">Editorial Intelligence</span>
        <nav className="top-links">
          {items.map((item) => (
            <button
              key={item.key}
              type="button"
              className={`top-link ${topView === item.key ? "active" : ""}`}
              onClick={() => setTopView(item.key)}
            >
              {item.label}
            </button>
          ))}
        </nav>
      </div>
      <div className="nav-right">
        <button className="button primary mini" onClick={() => setShowCreateModal(true)} type="button">
          Create KB
        </button>
        <button className="icon-btn" type="button" aria-label="Notifications"><span className="material-symbols-outlined">notifications</span></button>
        <button className="icon-btn" type="button" aria-label="Settings"><span className="material-symbols-outlined">settings</span></button>
        <button className="avatar-btn" onClick={onLogout} title="Logout" aria-label="Logout" type="button">
          {String(username ?? "U").slice(0, 1).toUpperCase()}
        </button>
      </div>
    </header>
  );
}

function SideNav({ activeTab, setWorkspaceTab, detail, onUploadPick }) {
  const tabs = [
    { key: "documents", label: "Documents", icon: "description" },
    { key: "chat", label: "Chat", icon: "forum" },
    { key: "insights", label: "Insights", icon: "lightbulb" },
    { key: "collections", label: "Collections", icon: "folder_special" },
    { key: "archive", label: "Archive", icon: "archive" }
  ];

  return (
    <aside className="side-nav">
      <div className="project-tile">
        <div className="project-icon">{(detail?.name ?? "A").slice(0, 1).toUpperCase()}</div>
        <div>
          <p className="project-name">{detail?.name ?? "Project Alpha"}</p>
          <p className="project-meta">Editorial Team</p>
        </div>
      </div>

      <button className="button primary wide side-create" onClick={onUploadPick} type="button">
        <span className="material-symbols-outlined">add</span>
        New Document
      </button>

      <nav className="side-links">
        {tabs.map((item) => (
          <button
            key={item.key}
            type="button"
            className={`side-link ${activeTab === item.key ? "active" : ""}`}
            onClick={() => setWorkspaceTab(item.key)}
          >
            <span className="material-symbols-outlined">{item.icon}</span>
            {item.label}
          </button>
        ))}
      </nav>

      <div className="side-footer">
        <button className="side-link" type="button"><span className="material-symbols-outlined">help_outline</span>Help</button>
        <button className="side-link" type="button"><span className="material-symbols-outlined">delete</span>Trash</button>
      </div>
    </aside>
  );
}

function LoginView({ authMode, setAuthMode, authForm, setAuthForm, handleAuthSubmit, busy, status }) {
  return (
    <main className="login-page">
      <div className="login-hero">
        <div className="login-icon"><span className="material-symbols-outlined">auto_awesome</span></div>
        <h1>Welcome to Editorial Intelligence</h1>
        <p>The professional workspace where AI-driven insights meet human curation.</p>
      </div>

      <section className="login-card">
        <form className="stack" onSubmit={handleAuthSubmit}>
          <div className="auth-mode-switch">
            <button type="button" className={authMode === "login" ? "active" : ""} onClick={() => setAuthMode("login")}>Login</button>
            <button type="button" className={authMode === "register" ? "active" : ""} onClick={() => setAuthMode("register")}>Register</button>
          </div>

          {authMode === "register" ? (
            <label className="field">
              <span>Username</span>
              <input
                minLength={3}
                maxLength={32}
                value={authForm.username}
                onChange={(event) => setAuthForm((current) => ({ ...current, username: event.target.value }))}
                required
              />
            </label>
          ) : null}

          <label className="field">
            <span>{authMode === "register" ? "Professional Email" : "Identity"}</span>
            <input
              type={authMode === "register" ? "email" : "text"}
              value={authForm.identity}
              onChange={(event) => setAuthForm((current) => ({ ...current, identity: event.target.value }))}
              placeholder={authMode === "register" ? "name@company.com" : "email or username"}
              required
            />
          </label>

          <label className="field">
            <span>Password</span>
            <input
              type="password"
              minLength={8}
              value={authForm.password}
              onChange={(event) => setAuthForm((current) => ({ ...current, password: event.target.value }))}
              required
            />
          </label>

          <button className="button primary wide" type="submit" disabled={busy.auth}>
            {busy.auth ? "Submitting..." : authMode === "register" ? "Create account" : "Login to Workspace"}
          </button>
        </form>
      </section>

      <div className={`status-banner login-status ${toneClass(status.tone)}`}>{status.message}</div>
    </main>
  );
}

function DashboardView({ knowledgeBases, documents, tasks, detail, failedTaskCount, setTopView, setWorkspaceTab }) {
  return (
    <section className="page dashboard-page">
      <div className="page-head split">
        <div>
          <h1>Workspace Overview</h1>
          <p>Manage your organizational intelligence and track document processing in real-time.</p>
        </div>
        <button
          className="button primary big"
          type="button"
          onClick={() => {
            setTopView("workspace");
            setWorkspaceTab("documents");
          }}
        >
          <span className="material-symbols-outlined">add_circle</span>
          Open Workspace
        </button>
      </div>

      <div className="metric-grid">
        <article className="metric-card"><p>KB Count</p><strong>{knowledgeBases.length}</strong><small>Active</small></article>
        <article className="metric-card"><p>Docs</p><strong>{documents.length}</strong><small>Processed</small></article>
        <article className="metric-card"><p>Tasks</p><strong>{tasks.length}</strong><small>Total</small></article>
      </div>

      <div className="dashboard-layout">
        <article className="feature-card">
          <div className="row">
            <div>
              <h2>{detail?.name ?? "No active KB"}</h2>
              <p>{detail?.description ?? "Create a knowledge base to begin."}</p>
            </div>
            <span className="chip owner">ACTIVE AI</span>
          </div>
          <div className="feature-meta">
            <div><span>Documents</span><strong>{documents.length} items</strong></div>
            <div><span>Failed Tasks</span><strong>{failedTaskCount}</strong></div>
            <div><span>Access</span><strong>{detail?.accessType ?? "n/a"}</strong></div>
          </div>
          <div className="row">
            <div className="avatar-group"><span>Y</span><span>S</span><span>+1</span></div>
            <button
              type="button"
              className="inline-link"
              onClick={() => {
                setTopView("workspace");
                setWorkspaceTab("documents");
              }}
            >
              Open Workspace <span className="material-symbols-outlined">arrow_forward</span>
            </button>
          </div>
        </article>

        <article className="task-card">
          <div className="task-card-head"><h3>Recent Tasks</h3><span className="inline-link">VIEW ALL</span></div>
          <div className="task-feed">
            {tasks.slice(0, 3).map((task) => (
              <div className="task-feed-item" key={task.id}>
                <div className={`dot ${task.status === "FAILED" ? "danger" : "success"}`} />
                <div>
                  <strong>{STATUS_LABELS[task.status] ?? task.status}</strong>
                  <p>{task.taskType} | {formatDateTime(task.createdAt)}</p>
                </div>
              </div>
            ))}
            {tasks.length === 0 ? (<EmptyState icon="task_alt" title="No recent tasks" description="Ingestion and indexing activity will appear here once documents start processing." />) : null}
          </div>
          <div className="didyouknow">
            <h4>Did you know?</h4>
            <p>You can cross-reference documents between different Knowledge Bases using global search.</p>
          </div>
        </article>
      </div>
    </section>
  );
}

function InsightsView({ documents, tasks, failedTaskCount }) {
  const completed = tasks.filter((item) => item.status === "SUCCEEDED").length;

  return (
    <section className="page insights-page">
      <div className="page-head split">
        <div><p className="crumb">Workspace &gt; Project Alpha</p><h1>Insights</h1></div>
        <div className="insight-actions"><button className="button ghost">Filter</button><button className="button primary">Share Report</button></div>
      </div>

      <div className="metric-grid four">
        <article className="metric-card"><p>Knowledge Health</p><strong>{Math.max(70, 100 - failedTaskCount * 6)}%</strong></article>
        <article className="metric-card"><p>Total Ingestions</p><strong>{tasks.length}</strong></article>
        <article className="metric-card"><p>Source Quality</p><strong>{failedTaskCount === 0 ? "High" : "Medium"}</strong></article>
        <article className="metric-card"><p>OCR Ratio</p><strong>{documents.length ? "99.2%" : "0%"}</strong></article>
      </div>

      <div className="insight-layout">
        <article className="panel chart-card">
          <div className="row"><h2>Ingestion Summary</h2><span className="chip muted">Last 7 Days</span></div>
          <div className="bar-chart">
            {[40, 58, 47, 73, 39, 82, 64].map((height, index) => (
              <div className="bar-wrap" key={index}><div className="bar" style={{ height: `${height}%` }} /></div>
            ))}
          </div>
          <div className="chart-labels"><span>MON</span><span>TUE</span><span>WED</span><span>THU</span><span>FRI</span><span>SAT</span><span>SUN</span></div>
        </article>

        <div className="insight-side">
          <article className="panel error-card">
            <div className="row"><h3>Failed Docs</h3><span className="chip danger">{failedTaskCount} items</span></div>
            <p>System encountered encoding errors in recently uploaded files.</p>
            <button className="button ghost wide">Retry Sync</button>
          </article>
          <article className="panel topic-card">
            <h3>Common Topics</h3>
            <div className="topic-list">
              {["Editorial Strategy", "AI Ethics", "Content Velocity", "Global Taxonomy", "Workflow Automation", "Style Guides"].map((topic) => (
                <span className="chip muted" key={topic}>{topic}</span>
              ))}
            </div>
          </article>
        </div>
      </div>

      <article className="panel activity-card">
        <h3>Activity Feed</h3>
        <div className="activity-list">
          <div className="activity-item"><strong>New ingestion completed</strong><p>Uploaded 14 technical specifications to the Core Engine collection.</p><small>12m ago</small></div>
          <div className="activity-item"><strong>Insight extraction finalized</strong><p>Generated {completed} completed ingestion snapshots for analytics review.</p><small>2h ago</small></div>
          <div className="activity-item"><strong>Collection updated</strong><p>Archived outdated documents and refreshed tags in Project Alpha.</p><small>5h ago</small></div>
        </div>
      </article>
    </section>
  );
}

function CollectionsView({ collections, setCollectionDetailOpen, setSelectedKnowledgeBaseId, setShowCreateModal }) {
  return (
    <section className="page collections-page">
      <div className="page-head split">
        <div><p className="crumb">Workspace / Collections</p><h1>Collections</h1></div>
        <button className="button primary big" type="button" onClick={() => setShowCreateModal(true)}><span className="material-symbols-outlined">create_new_folder</span>New Collection</button>
      </div>

      <div className="collection-grid">
        {collections.slice(0, 4).map((collection) => (
          <article className="collection-card" key={collection.id}>
            <div className={`collection-icon ${collection.tone}`}><span className="material-symbols-outlined">folder_special</span></div>
            <h3>{collection.name}</h3>
            <p>{collection.docs} Documents</p>
            <div className="collection-foot">
              <small>{collection.date}</small>
              <button
                className="icon-btn"
                type="button"
                onClick={() => {
                  setSelectedKnowledgeBaseId(collection.id);
                  setCollectionDetailOpen(true);
                }}
              >
                <span className="material-symbols-outlined">arrow_forward_ios</span>
              </button>
            </div>
          </article>
        ))}
      </div>

      <div className="empty-collection-panel">
        <div className="empty-icon"><span className="material-symbols-outlined">folder_off</span></div>
        <h3>No Collections here yet</h3>
        <p>Organize your research and documents by creating your first curated workspace collection.</p>
        <button className="button ghost" type="button" onClick={() => setShowCreateModal(true)}>Create your first collection</button>
      </div>
    </section>
  );
}

function CollectionDetailView({
  detail,
  selectedKnowledgeBase,
  documents,
  busy,
  setUploadFile,
  tableUploadRef,
  handleUploadSubmit,
  uploadFile,
  handleDeleteDocument,
  setCollectionDetailOpen
}) {
  const activeName = selectedKnowledgeBase?.name ?? detail?.name ?? "Research Papers";

  return (
    <section className="page collection-detail-page">
      <div className="page-head split">
        <div>
          <p className="crumb"><button className="inline-link" type="button" onClick={() => setCollectionDetailOpen(false)}>Collections</button> &gt; {activeName}</p>
          <h1>{activeName} <span className="count">({documents.length})</span></h1>
          <p>Core academic foundations and peer-reviewed journals for Project Alpha.</p>
        </div>

        <form className="inline-upload" onSubmit={handleUploadSubmit}>
          <input
            type="file"
            ref={tableUploadRef}
            accept=".pdf,.txt,.md,.doc,.docx,.xls,.xlsx"
            onChange={(event) => setUploadFile(event.target.files?.[0] ?? null)}
          />
          <button className="button primary" type="submit" disabled={busy.upload || !uploadFile}>{busy.upload ? "Uploading..." : "Add Document"}</button>
        </form>
      </div>

      <section className="table-card">
        <table>
          <thead><tr><th>Name</th><th>Size</th><th>Added Date</th><th /></tr></thead>
          <tbody>
            {documents.slice(0, 8).map((doc) => {
              const type = detectFileType(doc.contentType, doc.originalFilename);
              return (
                <tr key={doc.id}>
                  <td>
                    <div className="table-name">
                      <span className={`table-icon ${type}`}><span className="material-symbols-outlined">{fileIcon(type)}</span></span>
                      <div><strong>{doc.originalFilename}</strong><p>{doc.contentType || "Document"}</p></div>
                    </div>
                  </td>
                  <td>{formatBytes(doc.sizeBytes)}</td>
                  <td>{formatDate(doc.createdAt)}</td>
                  <td className="table-actions">
                    <span className={`chip ${doc.processingStatus === "FAILED" ? "danger" : "muted"}`}>{doc.processingStatus}</span>
                    {doc.processingStatus === "FAILED" ? (
                      <button
                        className="icon-btn danger"
                        type="button"
                        disabled={busy.deleteDocumentId === doc.id}
                        onClick={() => void handleDeleteDocument(doc)}
                      >
                        <span className="material-symbols-outlined">close</span>
                      </button>
                    ) : null}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {busy.workspace && documents.length === 0 ? <LoadingState title="Loading collection" lines={4} /> : null}{!busy.workspace && documents.length === 0 ? (<EmptyState icon="menu_book" title="Collection is empty" description="Add source files here to keep this collection scoped and searchable." />) : null}
      </section>

      <section className="detail-bottom">
        <article className="smart-card"><h3>Smart Summaries Active</h3><p>Our AI is currently processing your latest papers. You will receive a notification once executive insights are ready.</p><span className="chip muted">75% Complete</span></article>
        <article className="share-card"><h3>Shared with</h3><p>4 Team members have edit access</p><div className="avatar-group"><span>AM</span><span>SK</span><span>JS</span><span>+1</span></div></article>
      </section>
    </section>
  );
}

function ArchiveView({ documents, failedTaskCount }) {
  const archiveRows = documents.slice(0, 3).map((doc, index) => ({
    id: doc.id,
    name: doc.originalFilename,
    date: formatDate(doc.createdAt),
    size: formatBytes(doc.sizeBytes),
    icon: ["description", "draft", "table_chart"][index % 3]
  }));

  const fallback = [
    { id: "fallback-1", name: "Old_Strategy_2022.docx", date: "Oct 12, 2023", size: "4.2 MB", icon: "description" },
    { id: "fallback-2", name: "Q3_Marketing_Audit_v01.pdf", date: "Nov 02, 2023", size: "1.8 MB", icon: "draft" },
    { id: "fallback-3", name: "Budget_Projections_Backup.xlsx", date: "Dec 20, 2023", size: "856 KB", icon: "table_chart" }
  ];

  const list = archiveRows.length ? archiveRows : fallback;

  return (
    <section className="page archive-page">
      <div className="page-head split"><div><p className="crumb">Workspace / Project Alpha</p><h1>Archive</h1></div><span className="chip muted">{Math.max(3, failedTaskCount)} Items Archived</span></div>
      <div className="archive-list">{list.map((item) => <article className="archive-row" key={item.id}><div className="archive-icon"><span className="material-symbols-outlined">{item.icon}</span></div><div><h3>{item.name}</h3><p>Archived {item.date} - {item.size}</p></div></article>)}</div>
      <article className="archive-info"><div className="archive-info-icon"><span className="material-symbols-outlined">info</span></div><div><h3>About Project Archiving</h3><p>Archived items are removed from your active workspace but remain searchable in the global index. Restoring an item will place it back in its original collection.</p></div></article>
    </section>
  );
}

function LibraryView({ cards, setTopView, setWorkspaceTab, setSelectedKnowledgeBaseId }) {
  return (
    <section className="page library-page">
      <div className="page-head split">
        <div><h1>Library</h1><p>Your curated knowledge ecosystem. Access projects, internal documentation, and insights.</p></div>
        <button className="button primary big" type="button">New Asset</button>
      </div>

      <div className="library-filters">
        <div className="filter-tabs"><button className="active" type="button">All</button><button type="button">Owned by me</button><button type="button">Shared with me</button><button type="button">Recent</button></div>
        <div className="library-search"><span className="material-symbols-outlined">search</span><input placeholder="Filter library..." type="text" /></div>
      </div>

      <div className="library-grid">
        {cards.map((card) => (
          <article className="library-card" key={card.id}>
            <div className={`collection-icon ${card.tone ?? "blue"}`}><span className="material-symbols-outlined">folder_special</span></div>
            <h3>{card.name}</h3>
            <p>{card.docs} Documents</p>
            <div className="row">
              <small>Updated {card.date}</small>
              <button
                type="button"
                className="inline-link"
                onClick={() => {
                  setSelectedKnowledgeBaseId(card.id);
                  setTopView("workspace");
                  setWorkspaceTab("documents");
                }}
              >
                Open
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function WorkspaceView({
  detail,
  knowledgeBases,
  selectedKnowledgeBaseId,
  setSelectedKnowledgeBaseId,
  documents,
  tasks,
  busy,
  uploadFile,
  setUploadFile,
  fileInputRef,
  handleUploadSubmit,
  handleDeleteDocument,
  handleRetryTask,
  sessions,
  question,
  setQuestion,
  handleQaSubmit,
  stopQa,
  onOpenChat,
  docsPanelVisible,
  setDocsPanelVisible
}) {
  return (
    <section className="page workspace-docs">
      <div className="page-head split">
        <div>
          <h1>{detail?.name ?? "Project Alpha"}</h1>
          <p>{detail?.description ?? "Choose a knowledge base to start."}</p>
        </div>
        <div className="page-head-actions">
          <select
            className="select-input"
            value={selectedKnowledgeBaseId ?? ""}
            onChange={(event) => setSelectedKnowledgeBaseId(event.target.value)}
          >
            {knowledgeBases.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
          <button className="button ghost small" type="button" onClick={() => setDocsPanelVisible((current) => !current)}>
            <span className="material-symbols-outlined">{docsPanelVisible ? "left_panel_close" : "left_panel_open"}</span>
            {docsPanelVisible ? "Hide Docs" : "Show Docs"}
          </button>
          <span className={`chip ${detail?.accessType === "OWNER" ? "owner" : "shared"}`}>{detail?.accessType ?? "n/a"}</span>
        </div>
      </div>

      <div className={`workspace-layout ${docsPanelVisible ? "" : "docs-hidden"}`}>
        {docsPanelVisible ? (
        <div className="left-column">
          <section className="panel card">
            <div className="card-head"><h2>Documents</h2><span className="chip muted">{documents.length} docs</span></div>
            <form className="upload-form" onSubmit={handleUploadSubmit}>
              <input
                type="file"
                ref={fileInputRef}
                accept=".pdf,.txt,.md,.doc,.docx,.xls,.xlsx"
                onChange={(event) => setUploadFile(event.target.files?.[0] ?? null)}
              />
              <button className="button primary wide" type="submit" disabled={!detail || detail.accessType !== "OWNER" || busy.upload || !uploadFile}>{busy.upload ? "Uploading..." : "Upload"}</button>
            </form>
            {detail?.accessType !== "OWNER" ? <p className="hint">Shared viewer can ask questions only, upload is disabled.</p> : null}

            <div className="doc-list">
              {documents.slice(0, 6).map((doc) => {
                const type = detectFileType(doc.contentType, doc.originalFilename);
                return (
                  <article className="doc-item" key={doc.id}>
                    <div className={`doc-icon ${type}`}><span className="material-symbols-outlined">{fileIcon(type)}</span></div>
                    <div className="doc-main"><strong>{doc.originalFilename}</strong><p>{formatBytes(doc.sizeBytes)}</p></div>
                    <div className="doc-actions">
                      <span className={`chip ${doc.processingStatus === "FAILED" ? "danger" : "muted"}`}>{doc.processingStatus}</span>
                      {doc.processingStatus === "FAILED" ? <button className="button ghost small" type="button" onClick={() => void handleDeleteDocument(doc)} disabled={busy.deleteDocumentId === doc.id}>Delete</button> : null}
                    </div>
                  </article>
                );
              })}
              {busy.workspace && documents.length === 0 ? <LoadingState title="Loading documents" lines={4} /> : null}{!busy.workspace && documents.length === 0 ? (<EmptyState icon="upload_file" title="No documents yet" description="Upload files to start indexing content and unlock question answering." action={detail?.accessType === "OWNER" ? <span className="chip owner">Owner can upload</span> : null} />) : null}
            </div>
          </section>

          <section className="panel card">
            <div className="card-head"><h2>Task Status</h2><span className="chip muted">{tasks.length}</span></div>
            <div className="task-list">
              {tasks.slice(0, 5).map((task) => (
                <article className="task-row" key={task.id}>
                  <div>
                    <strong>{STATUS_LABELS[task.status] ?? task.status}</strong>
                    <p>{task.taskType} | {STAGE_LABELS[task.currentStage] ?? task.currentStage ?? "n/a"}</p>
                    <small>{formatDateTime(task.createdAt)}</small>
                    {task.failureMessage ? <div className="inline-error">{task.failureMessage}</div> : null}
                  </div>
                  {task.status === "FAILED" ? <button className="button ghost small" type="button" onClick={() => void handleRetryTask(task)} disabled={busy.retryTaskId === task.id}>Retry</button> : <span className="chip muted">{task.status}</span>}
                </article>
              ))}
              {busy.workspace && tasks.length === 0 ? <LoadingState title="Loading tasks" lines={3} /> : null}{!busy.workspace && tasks.length === 0 ? (<EmptyState icon="schedule" title="No ingestion tasks" description="Task history will appear here after uploads, OCR, and indexing jobs run." />) : null}
            </div>
          </section>
        </div>

        ) : null}

        <section className={`panel card chat-panel ${docsPanelVisible ? "" : "expanded"}`}>
          <div className="chat-head">
            <div>
              <h2>Ask the Knowledge Base</h2>
              <p>Connected to {documents.length} documents</p>
            </div>
            <button className="button ghost small" type="button" onClick={onOpenChat}>
              <span className="material-symbols-outlined">open_in_full</span>
              Open Chat
            </button>
          </div>
          <div className="chat-feed">
            {sessions.map((session) => (
              <article className="qa-item" key={session.id}>
                <div className="qa-user"><div className="bubble user-bubble">{session.question}</div><small>{formatDateTime(session.createdAt)}</small></div>
                <div className="qa-assistant">
                  <div className="bubble ai-bubble">{session.answer || "Generating answer..."}</div>
                  {session.sources.length ? (
                    <div className="source-pack">
                      <h4>Sources</h4>
                      {session.sources.map((source, index) => (
                        <div className="source-row" key={`${session.id}-${source.segmentId ?? index}`}>
                          <strong>{source.documentName || "untitled"}</strong>
                          <span className="chip muted">score {source.score?.toFixed?.(2) ?? "n/a"}</span>
                          <p>{source.content}</p>
                        </div>
                      ))}
                    </div>
                  ) : null}
                  {session.error ? <div className="inline-error">{session.error}</div> : null}
                </div>
              </article>
            ))}
            {sessions.length === 0 ? (<EmptyState icon="forum" title="Start a focused conversation" description="Ask about uploaded documents and the assistant will answer with citations and streamed responses." />) : null}
          </div>

          <form className="chat-input" onSubmit={handleQaSubmit}>
            <textarea rows={3} value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="Ask anything about your documents..." />
            <div className="chat-actions">
              <button className="button ghost" type="button" onClick={stopQa}>Stop</button>
              <button className="button primary" type="submit" disabled={busy.qa || !question.trim()}>{busy.qa ? "Streaming..." : "Start stream"}</button>
            </div>
          </form>
        </section>
      </div>
    </section>
  );
}

export default function App() {
  const [authMode, setAuthMode] = useState("login");
  const [authForm, setAuthForm] = useState(EMPTY_AUTH_FORM);
  const [auth, setAuth] = useState(() => readAuth());

  const [knowledgeBaseForm, setKnowledgeBaseForm] = useState(EMPTY_KB_FORM);
  const [knowledgeBases, setKnowledgeBases] = useState([]);
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [tasks, setTasks] = useState([]);

  const [uploadFile, setUploadFile] = useState(null);
  const [question, setQuestion] = useState("");
  const [sessions, setSessions] = useState([]);

  const [busy, setBusy] = useState({});
  const [status, setStatus] = useState({ tone: "neutral", message: "Ready." });

  const [topView, setTopView] = useState("workspace");
  const [workspaceTab, setWorkspaceTab] = useState("documents");
  const [docsPanelVisible, setDocsPanelVisible] = useState(true);
  const [collectionDetailOpen, setCollectionDetailOpen] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);

  const fileInputRef = useRef(null);
  const tableUploadRef = useRef(null);
  const qaAbortRef = useRef(null);

  const selectedKnowledgeBase = useMemo(
    () => knowledgeBases.find((item) => item.id === selectedKnowledgeBaseId) ?? null,
    [knowledgeBases, selectedKnowledgeBaseId]
  );

  const activeTaskCount = tasks.filter((task) => ACTIVE_TASK_STATUSES.has(task.status)).length;
  const failedTaskCount = tasks.filter((task) => task.status === "FAILED").length;

  const collections = useMemo(() => {
    if (!knowledgeBases.length) {
      return [{ id: "placeholder", name: "Research Papers", docs: 0, date: "No data", tone: "blue" }];
    }
    return knowledgeBases.map((item, index) => ({
      id: item.id,
      name: item.name,
      docs: item.documentCount ?? (selectedKnowledgeBaseId === item.id ? documents.length : 0),
      date: formatDate(item.updatedAt ?? item.createdAt),
      tone: ["blue", "purple", "green", "orange"][index % 4]
    }));
  }, [documents.length, knowledgeBases, selectedKnowledgeBaseId]);

  useEffect(() => {
    if (!auth?.token) {
      setKnowledgeBases([]);
      setSelectedKnowledgeBaseId(null);
      setDetail(null);
      setDocuments([]);
      setTasks([]);
      setSessions([]);
      return;
    }

    let cancelled = false;
    void (async () => {
      setBusy((current) => ({ ...current, list: true }));
      try {
        const listResult = await api("/api/v1/knowledge-bases", { token: auth.token });
        const list = listResult?.data ?? [];
        if (cancelled) return;
        setKnowledgeBases(list);
        setSelectedKnowledgeBaseId((current) => current && list.some((item) => item.id === current) ? current : list[0]?.id ?? null);
      } catch (error) {
        if (!cancelled) setStatus({ tone: "danger", message: `Load KB failed: ${error.message}` });
      } finally {
        if (!cancelled) setBusy((current) => ({ ...current, list: false }));
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [auth?.token]);

  useEffect(() => {
    if (!auth?.token || !selectedKnowledgeBaseId) {
      setDetail(null);
      setDocuments([]);
      setTasks([]);
      return;
    }

    let cancelled = false;
    void (async () => {
      setBusy((current) => ({ ...current, workspace: true }));
      try {
        const [detailResult, documentsResult, tasksResult] = await Promise.all([
          api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}`, { token: auth.token }),
          api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}/documents`, { token: auth.token }),
          api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}/ingestion-tasks`, { token: auth.token })
        ]);
        if (cancelled) return;
        setDetail(detailResult?.data ?? null);
        setDocuments(documentsResult?.data ?? []);
        setTasks(tasksResult?.data ?? []);
      } catch (error) {
        if (!cancelled) setStatus({ tone: "danger", message: `Load workspace failed: ${error.message}` });
      } finally {
        if (!cancelled) setBusy((current) => ({ ...current, workspace: false }));
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [auth?.token, selectedKnowledgeBaseId]);

  useEffect(() => {
    if (!auth?.token || !selectedKnowledgeBaseId || activeTaskCount === 0) return undefined;
    const timer = window.setInterval(async () => {
      try {
        const [detailResult, documentsResult, tasksResult] = await Promise.all([
          api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}`, { token: auth.token }),
          api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}/documents`, { token: auth.token }),
          api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}/ingestion-tasks`, { token: auth.token })
        ]);
        setDetail(detailResult?.data ?? null);
        setDocuments(documentsResult?.data ?? []);
        setTasks(tasksResult?.data ?? []);
      } catch {
        // keep polling silent
      }
    }, 2500);
    return () => window.clearInterval(timer);
  }, [activeTaskCount, auth?.token, selectedKnowledgeBaseId]);

  useEffect(() => () => qaAbortRef.current?.abort(), []);

  async function refreshWorkspaceNow() {
    if (!auth?.token || !selectedKnowledgeBaseId) return;
    const [detailResult, documentsResult, tasksResult] = await Promise.all([
      api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}`, { token: auth.token }),
      api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}/documents`, { token: auth.token }),
      api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}/ingestion-tasks`, { token: auth.token })
    ]);
    setDetail(detailResult?.data ?? null);
    setDocuments(documentsResult?.data ?? []);
    setTasks(tasksResult?.data ?? []);
  }

  async function refreshKnowledgeBases(forceSelectId) {
    if (!auth?.token) return;
    const listResult = await api("/api/v1/knowledge-bases", { token: auth.token });
    const list = listResult?.data ?? [];
    setKnowledgeBases(list);
    setSelectedKnowledgeBaseId((current) => {
      if (forceSelectId && list.some((item) => item.id === forceSelectId)) return forceSelectId;
      if (current && list.some((item) => item.id === current)) return current;
      return list[0]?.id ?? null;
    });
  }

  async function handleAuthSubmit(event) {
    event.preventDefault();
    setBusy((current) => ({ ...current, auth: true }));
    try {
      const path = authMode === "register" ? "/api/v1/auth/register" : "/api/v1/auth/login";
      const payload = authMode === "register"
        ? { username: authForm.username.trim(), email: authForm.identity.trim(), password: authForm.password }
        : { identity: authForm.identity.trim(), password: authForm.password };
      const result = await api(path, { method: "POST", body: JSON.stringify(payload) });
      const nextAuth = { token: result.data.accessToken, user: result.data.user };
      writeAuth(nextAuth);
      setAuth(nextAuth);
      setAuthForm(EMPTY_AUTH_FORM);
      setStatus({ tone: "success", message: authMode === "register" ? "Registration successful, please login." : "Login successful." });
    } catch (error) {
      setStatus({ tone: "danger", message: `${authMode === "register" ? "Registration" : "Login"} failed: ${error.message}` });
    } finally {
      setBusy((current) => ({ ...current, auth: false }));
    }
  }

  function handleLogout() {
    qaAbortRef.current?.abort();
    qaAbortRef.current = null;
    writeAuth(null);
    setAuth(null);
    setSessions([]);
    setUploadFile(null);
    setQuestion("");
    setTopView("workspace");
    setWorkspaceTab("documents");
    setStatus({ tone: "neutral", message: "Logged out." });
  }

  async function handleKnowledgeBaseCreate(event) {
    event.preventDefault();
    if (!auth?.token) return;
    setBusy((current) => ({ ...current, create: true }));
    try {
      const result = await api("/api/v1/knowledge-bases", {
        method: "POST",
        token: auth.token,
        body: JSON.stringify({
          name: knowledgeBaseForm.name.trim(),
          description: knowledgeBaseForm.description.trim()
        })
      });
      setKnowledgeBaseForm(EMPTY_KB_FORM);
      await refreshKnowledgeBases(result.data.id);
      setShowCreateModal(false);
      setStatus({ tone: "success", message: `KB "${result.data.name}" created` });
    } catch (error) {
      setStatus({ tone: "danger", message: `Create KB failed: ${error.message}` });
    } finally {
      setBusy((current) => ({ ...current, create: false }));
    }
  }

  async function handleUploadSubmit(event) {
    event.preventDefault();
    if (!auth?.token || !selectedKnowledgeBaseId || !uploadFile) return;
    setBusy((current) => ({ ...current, upload: true }));
    const formData = new FormData();
    formData.append("file", uploadFile);
    try {
      const result = await api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}/documents`, {
        method: "POST",
        token: auth.token,
        formData
      });
      setUploadFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
      if (tableUploadRef.current) tableUploadRef.current.value = "";
      await refreshWorkspaceNow();
      setStatus({ tone: "success", message: `Uploaded ${result.data.document.originalFilename}` });
    } catch (error) {
      setStatus({ tone: "danger", message: `Upload failed: ${error.message}` });
    } finally {
      setBusy((current) => ({ ...current, upload: false }));
    }
  }

  async function handleDeleteDocument(document) {
    if (!auth?.token || !selectedKnowledgeBaseId) return;
    setBusy((current) => ({ ...current, deleteDocumentId: document.id }));
    try {
      await api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}/documents/${document.id}`, {
        method: "DELETE",
        token: auth.token
      });
      await refreshWorkspaceNow();
      setStatus({ tone: "success", message: `Deleted ${document.originalFilename}` });
    } catch (error) {
      setStatus({ tone: "danger", message: `Delete failed: ${error.message}` });
    } finally {
      setBusy((current) => ({ ...current, deleteDocumentId: null }));
    }
  }

  async function handleRetryTask(task) {
    if (!auth?.token || !selectedKnowledgeBaseId) return;
    setBusy((current) => ({ ...current, retryTaskId: task.id }));
    try {
      await api(`/api/v1/knowledge-bases/${selectedKnowledgeBaseId}/ingestion-tasks/${task.id}/retry`, {
        method: "POST",
        token: auth.token
      });
      await refreshWorkspaceNow();
      setStatus({ tone: "success", message: `Retry requested: ${task.id}` });
    } catch (error) {
      setStatus({ tone: "danger", message: `Retry failed: ${error.message}` });
    } finally {
      setBusy((current) => ({ ...current, retryTaskId: null }));
    }
  }

  function applyQaEvent(sessionId, nextEvent) {
    setSessions((current) =>
      current.map((session) => {
        if (session.id !== sessionId) return session;
        if (nextEvent.event === "sources") {
          return { ...session, sources: Array.isArray(nextEvent.payload) ? nextEvent.payload : [] };
        }
        if (nextEvent.event === "message") {
          return { ...session, answer: `${session.answer}${nextEvent.payload?.delta ?? ""}` };
        }
        if (nextEvent.event === "done") {
          return {
            ...session,
            answer: nextEvent.payload?.answer ?? session.answer,
            refusal: Boolean(nextEvent.payload?.refusal),
            status: "done"
          };
        }
        if (nextEvent.event === "error") {
          return {
            ...session,
            status: "error",
            error: nextEvent.payload?.message ?? "Stream interrupted"
          };
        }
        return session;
      })
    );
  }

  async function handleQaSubmit(event) {
    event.preventDefault();
    if (!auth?.token || !selectedKnowledgeBaseId || !question.trim()) return;

    qaAbortRef.current?.abort();
    const controller = new AbortController();
    qaAbortRef.current = controller;

    const prompt = question.trim();
    const sessionId = makeId();
    setQuestion("");
    setBusy((current) => ({ ...current, qa: true }));

    setSessions((current) => [
      {
        id: sessionId,
        question: prompt,
        answer: "",
        sources: [],
        refusal: false,
        status: "streaming",
        error: null,
        createdAt: new Date().toISOString()
      },
      ...current
    ]);

    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/knowledge-bases/${selectedKnowledgeBaseId}/qa/stream`, {
        method: "POST",
        headers: {
          Accept: "text/event-stream",
          "Content-Type": "application/json",
          Authorization: `Bearer ${auth.token}`
        },
        body: JSON.stringify({ query: prompt }),
        signal: controller.signal
      });

      if (!response.ok) {
        const text = await response.text();
        throw new Error(getErrorMessage(text, parseJson(text)));
      }

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      while (reader) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true }).replaceAll("\r\n", "\n");
        let splitIndex = buffer.indexOf("\n\n");
        while (splitIndex >= 0) {
          const block = buffer.slice(0, splitIndex).trim();
          buffer = buffer.slice(splitIndex + 2);
          if (block) applyQaEvent(sessionId, parseEventBlock(block));
          splitIndex = buffer.indexOf("\n\n");
        }
      }

      if (buffer.trim()) applyQaEvent(sessionId, parseEventBlock(buffer.trim()));
      setStatus({ tone: "success", message: "QA completed" });
    } catch (error) {
      if (error.name !== "AbortError") {
        setSessions((current) =>
          current.map((session) =>
            session.id === sessionId ? { ...session, status: "error", error: error.message } : session
          )
        );
        setStatus({ tone: "danger", message: `QA failed: ${error.message}` });
      }
    } finally {
      if (qaAbortRef.current === controller) qaAbortRef.current = null;
      setBusy((current) => ({ ...current, qa: false }));
    }
  }

  if (!auth?.token) {
    return (
      <LoginView
        authMode={authMode}
        setAuthMode={setAuthMode}
        authForm={authForm}
        setAuthForm={setAuthForm}
        handleAuthSubmit={handleAuthSubmit}
        busy={busy}
        status={status}
      />
    );
  }

  const showSidebar = topView !== "library";

  return (
    <div className="ei-app">
      <TopNav
        topView={topView}
        setTopView={(value) => {
          setTopView(value);
          if (value !== "workspace") setCollectionDetailOpen(false);
        }}
        setShowCreateModal={setShowCreateModal}
        onLogout={handleLogout}
        username={auth.user?.username}
      />

      <div className={`ei-shell ${showSidebar ? "" : "library-shell"}`}>
        {showSidebar ? (
          <SideNav
            activeTab={workspaceTab}
            setWorkspaceTab={(tab) => {
              setTopView("workspace");
              setWorkspaceTab(tab);
              if (tab !== "collections") setCollectionDetailOpen(false);
            }}
            detail={detail}
            onUploadPick={() => {
              setTopView("workspace");
              setWorkspaceTab("documents");
              window.requestAnimationFrame(() => fileInputRef.current?.click());
            }}
          />
        ) : null}

        <main className="ei-main">
          <div className={`status-banner ${toneClass(status.tone)}`}>{status.message}</div>

          {topView === "dashboard" ? (
            <DashboardView
              knowledgeBases={knowledgeBases}
              documents={documents}
              tasks={tasks}
              detail={detail}
              failedTaskCount={failedTaskCount}
              setTopView={setTopView}
              setWorkspaceTab={setWorkspaceTab}
            />
          ) : null}

          {topView === "library" ? (
            <LibraryView
              cards={collections}
              setTopView={setTopView}
              setWorkspaceTab={setWorkspaceTab}
              setSelectedKnowledgeBaseId={setSelectedKnowledgeBaseId}
            />
          ) : null}

          {topView === "workspace" && workspaceTab === "documents" ? (
            <WorkspaceView
              detail={detail}
              knowledgeBases={knowledgeBases}
              selectedKnowledgeBaseId={selectedKnowledgeBaseId}
              setSelectedKnowledgeBaseId={(value) => {
                setSelectedKnowledgeBaseId(value);
                setSessions([]);
              }}
              documents={documents}
              tasks={tasks}
              busy={busy}
              uploadFile={uploadFile}
              setUploadFile={setUploadFile}
              fileInputRef={fileInputRef}
              handleUploadSubmit={handleUploadSubmit}
              handleDeleteDocument={handleDeleteDocument}
              handleRetryTask={handleRetryTask}
              sessions={sessions}
              question={question}
              setQuestion={setQuestion}
              handleQaSubmit={handleQaSubmit}
              stopQa={() => qaAbortRef.current?.abort()}
              onOpenChat={() => setWorkspaceTab("chat")}
              docsPanelVisible={docsPanelVisible}
              setDocsPanelVisible={setDocsPanelVisible}
            />
          ) : null}

          {topView === "workspace" && workspaceTab === "chat" ? (
            <ChatView
              documents={documents}
              sessions={sessions}
              question={question}
              setQuestion={setQuestion}
              handleQaSubmit={handleQaSubmit}
              stopQa={() => qaAbortRef.current?.abort()}
              busy={busy}
            />
          ) : null}

          {topView === "workspace" && workspaceTab === "insights" ? (
            <InsightsView documents={documents} tasks={tasks} failedTaskCount={failedTaskCount} />
          ) : null}

          {topView === "workspace" && workspaceTab === "collections" && !collectionDetailOpen ? (
            <CollectionsView
              collections={collections}
              setCollectionDetailOpen={setCollectionDetailOpen}
              setSelectedKnowledgeBaseId={setSelectedKnowledgeBaseId}
              setShowCreateModal={setShowCreateModal}
            />
          ) : null}

          {topView === "workspace" && workspaceTab === "collections" && collectionDetailOpen ? (
            <CollectionDetailView
              detail={detail}
              selectedKnowledgeBase={selectedKnowledgeBase}
              documents={documents}
              busy={busy}
              setUploadFile={setUploadFile}
              tableUploadRef={tableUploadRef}
              handleUploadSubmit={handleUploadSubmit}
              uploadFile={uploadFile}
              handleDeleteDocument={handleDeleteDocument}
              setCollectionDetailOpen={setCollectionDetailOpen}
            />
          ) : null}

          {topView === "workspace" && workspaceTab === "archive" ? (
            <ArchiveView documents={documents} failedTaskCount={failedTaskCount} />
          ) : null}
        </main>
      </div>

      {showCreateModal ? (
        <div className="modal-backdrop" role="presentation" onClick={() => setShowCreateModal(false)}>
          <div className="modal-card" role="dialog" onClick={(event) => event.stopPropagation()}>
            <div className="modal-head"><h3>Create Knowledge Base</h3><button className="icon-btn" type="button" onClick={() => setShowCreateModal(false)}><span className="material-symbols-outlined">close</span></button></div>
            <form className="stack" onSubmit={handleKnowledgeBaseCreate}>
              <label className="field">
                <span>Name</span>
                <input
                  maxLength={64}
                  value={knowledgeBaseForm.name}
                  onChange={(event) => setKnowledgeBaseForm((current) => ({ ...current, name: event.target.value }))}
                  required
                />
              </label>
              <label className="field">
                <span>Description</span>
                <textarea
                  rows={4}
                  maxLength={240}
                  value={knowledgeBaseForm.description}
                  onChange={(event) => setKnowledgeBaseForm((current) => ({ ...current, description: event.target.value }))}
                />
              </label>
              <button className="button primary wide" type="submit" disabled={busy.create}>{busy.create ? "Creating..." : "Create KB"}</button>
            </form>
          </div>
        </div>
      ) : null}
    </div>
  );
}





function ChatView({ documents, sessions, question, setQuestion, handleQaSubmit, stopQa, busy }) {
  return (
    <section className="page chat-workspace">
      <div className="page-head split">
        <div>
          <h1>Chat Workspace</h1>
          <p>Ask questions in a focused chat workspace with streaming answers and sources.</p>
        </div>
        <span className="chip owner">{documents.length} docs connected</span>
      </div>

      <section className="panel card chat-panel full-chat-panel">
        <div className="chat-head">
          <div>
            <h2>Ask the Knowledge Base</h2>
            <p>Connected to {documents.length} documents</p>
          </div>
        </div>

        <div className="chat-feed">
          {sessions.map((session) => (
            <article className="qa-item" key={session.id}>
              <div className="qa-user">
                <div className="bubble user-bubble">{session.question}</div>
                <small>{formatDateTime(session.createdAt)}</small>
              </div>
              <div className="qa-assistant">
                <div className="bubble ai-bubble">{session.answer || "Generating answer..."}</div>
                {session.sources.length ? (
                  <div className="source-pack">
                    <h4>Sources</h4>
                    {session.sources.map((source, index) => (
                      <div className="source-row" key={`${session.id}-${source.segmentId ?? index}`}>
                        <strong>{source.documentName || "untitled"}</strong>
                        <span className="chip muted">score {source.score?.toFixed?.(2) ?? "n/a"}</span>
                        <p>{source.content}</p>
                      </div>
                    ))}
                  </div>
                ) : null}
                {session.error ? <div className="inline-error">{session.error}</div> : null}
              </div>
            </article>
          ))}
          {sessions.length === 0 ? (<EmptyState icon="chat" title="Chat is ready" description="Use this focused mode when you want a wider answer area and uninterrupted citation review." />) : null}
        </div>

        <form className="chat-input" onSubmit={handleQaSubmit}>
          <textarea
            rows={4}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="Ask anything about your documents..."
          />
          <div className="chat-actions">
            <button className="button ghost" type="button" onClick={stopQa}>Stop</button>
            <button className="button primary" type="submit" disabled={busy.qa || !question.trim()}>
              {busy.qa ? "Streaming..." : "Start stream"}
            </button>
          </div>
        </form>
      </section>
    </section>
  );
}










