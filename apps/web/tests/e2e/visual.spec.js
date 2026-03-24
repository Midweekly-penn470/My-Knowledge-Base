import { expect, test } from "@playwright/test";

const API_BASE = "http://localhost:8081";

async function mockApp(page) {
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
              originalFilename: "alpha.txt",
              sizeBytes: 272,
              contentType: "text/plain",
              processingStatus: "SUCCEEDED",
              createdAt: "2026-03-20T00:00:00.000Z"
            },
            {
              id: "doc-2",
              originalFilename: "beta.txt",
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
}

test.beforeEach(async ({ page }) => {
  await mockApp(page);
});

test("workspace visual baseline stays stable", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 960 });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "test" }).first()).toBeVisible();
  await expect(page.locator(".workspace-docs")).toHaveScreenshot("workspace-docs.png", {
    animations: "disabled"
  });
});

test("chat workspace visual baseline stays stable", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 960 });
  await page.goto("/");
  await page.getByRole("button", { name: /Open Chat/i }).click();
  await expect(page.getByRole("heading", { name: "Chat Workspace" })).toBeVisible();
  await expect(page.locator(".chat-workspace")).toHaveScreenshot("chat-workspace.png", {
    animations: "disabled"
  });
});