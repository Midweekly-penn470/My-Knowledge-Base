import { expect, test } from "@playwright/test";

const API_BASE = "http://localhost:8081";

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const auth = {
      token: "mock-token",
      user: { id: "user-1", username: "yorushika" }
    };
    window.localStorage.setItem("mykb.auth", JSON.stringify(auth));
  });

  await page.route(`${API_BASE}/api/v1/**`, async (route) => {
    const request = route.request();
    const { pathname } = new URL(request.url());

    if (request.method() === "GET" && pathname === "/api/v1/knowledge-bases") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: "kb-1",
              name: "test",
              description: "mock kb",
              accessType: "OWNER",
              createdAt: "2026-03-20T00:00:00.000Z",
              updatedAt: "2026-03-23T00:00:00.000Z",
              documentCount: 2
            }
          ]
        })
      });
      return;
    }

    if (request.method() === "GET" && pathname === "/api/v1/knowledge-bases/kb-1") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            id: "kb-1",
            name: "test",
            description: "mock kb",
            accessType: "OWNER"
          }
        })
      });
      return;
    }

    if (request.method() === "GET" && pathname === "/api/v1/knowledge-bases/kb-1/documents") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: "doc-1",
              originalFilename: "test-a.txt",
              sizeBytes: 272,
              contentType: "text/plain",
              processingStatus: "SUCCEEDED",
              createdAt: "2026-03-20T00:00:00.000Z"
            },
            {
              id: "doc-2",
              originalFilename: "test-b.txt",
              sizeBytes: 106,
              contentType: "text/plain",
              processingStatus: "SUCCEEDED",
              createdAt: "2026-03-20T01:00:00.000Z"
            }
          ]
        })
      });
      return;
    }

    if (request.method() === "GET" && pathname === "/api/v1/knowledge-bases/kb-1/ingestion-tasks") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [] })
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({ message: `unhandled mock route: ${request.method()} ${pathname}` })
    });
  });
});

test("workspace hides docs panel and keeps chat usable", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "test" }).first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Documents" })).toBeVisible();

  await page.getByRole("button", { name: "Hide Docs" }).click();
  await expect(page.getByRole("button", { name: "Show Docs" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Documents" })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Ask the Knowledge Base" })).toBeVisible();

  for (const viewport of [{ width: 1280, height: 900 }, { width: 960, height: 800 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport);
    await page.reload();
    await expect(page.getByRole("heading", { name: "test" }).first()).toBeVisible();
    await expect(page.getByRole("heading", { name: "Ask the Knowledge Base" })).toBeVisible();
    const hasHorizontalOverflow = await page.evaluate(() => {
      const root = document.documentElement;
      return root.scrollWidth - root.clientWidth > 1;
    });
    expect(hasHorizontalOverflow).toBe(false);
  }
});

test("responsive navigation and full chat workspace are available", async ({ page }) => {
  await page.goto("/");

  await expect(page.locator(".side-nav")).toBeVisible();
  await page.getByRole("button", { name: /Open Chat/i }).click();
  await expect(page.getByRole("heading", { name: "Chat Workspace" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Ask the Knowledge Base" })).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await expect(page.locator(".side-nav")).toBeHidden();
  await expect(page.getByRole("heading", { name: "test" }).first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Ask the Knowledge Base" })).toBeVisible();
});

test("workspace primary controls stay reachable across breakpoints", async ({ page }) => {
  const breakpoints = [
    { width: 1440, height: 960, sidebarVisible: true },
    { width: 1280, height: 900, sidebarVisible: true },
    { width: 1024, height: 820, sidebarVisible: true },
    { width: 768, height: 1024, sidebarVisible: false },
    { width: 390, height: 844, sidebarVisible: false }
  ];

  for (const viewport of breakpoints) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await page.goto("/");

    await expect(page.getByRole("heading", { name: "test" }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Hide Docs|Show Docs/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Ask the Knowledge Base" })).toBeVisible();
    await expect(page.getByPlaceholder("Ask anything about your documents...")).toBeVisible();

    const sideNav = page.locator(".side-nav");
    if (viewport.sidebarVisible) {
      await expect(sideNav).toBeVisible();
    } else {
      await expect(sideNav).toBeHidden();
    }

    const docsToggle = page.getByRole("button", { name: /Hide Docs|Show Docs/i });
    await docsToggle.focus();
    await expect(docsToggle).toBeFocused();

    const hasHorizontalOverflow = await page.evaluate(() => {
      const root = document.documentElement;
      return root.scrollWidth - root.clientWidth > 1;
    });
    expect(hasHorizontalOverflow).toBe(false);
  }
});