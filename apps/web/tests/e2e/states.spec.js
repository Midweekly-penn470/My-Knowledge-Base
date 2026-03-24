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
              documentCount: 3
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
            },
            {
              id: "doc-3",
              originalFilename: "failed.txt",
              sizeBytes: 64,
              contentType: "text/plain",
              processingStatus: "FAILED",
              createdAt: "2026-03-20T02:00:00.000Z"
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
        body: JSON.stringify({
          data: [
            {
              id: "task-1",
              taskType: "DOCUMENT_INGESTION",
              status: "FAILED",
              currentStage: "DIFY_UPLOAD",
              createdAt: "2026-03-20T03:00:00.000Z",
              failureMessage: "Mock upload failure"
            },
            {
              id: "task-2",
              taskType: "DOCUMENT_INGESTION",
              status: "SUCCEEDED",
              currentStage: "COMPLETED",
              createdAt: "2026-03-20T04:00:00.000Z",
              failureMessage: null
            }
          ]
        })
      });
      return;
    }

    if (request.method() === "DELETE" && pathname === "/api/v1/knowledge-bases/kb-1/documents/doc-3") {
      await route.fulfill({ status: 204, body: "" });
      return;
    }

    if (request.method() === "POST" && pathname === "/api/v1/knowledge-bases/kb-1/ingestion-tasks/task-1/retry") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: { accepted: true } })
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

test("workspace exposes disabled, error, and retry states", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Documents" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Upload" })).toBeDisabled();
  await expect(page.getByText("failed.txt")).toBeVisible();
  await expect(page.getByRole("button", { name: "Delete", exact: true })).toBeVisible();
  await expect(page.getByText("Mock upload failure")).toBeVisible();
  await expect(page.getByRole("button", { name: "Retry" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Start stream" })).toBeDisabled();

  await page.getByPlaceholder("Ask anything about your documents...").fill("Summarize alpha");
  await expect(page.getByRole("button", { name: "Start stream" })).toBeEnabled();

  await page.getByRole("button", { name: "Hide Docs" }).click();
  await expect(page.getByRole("button", { name: "Show Docs" })).toBeVisible();
  await page.getByRole("button", { name: "Show Docs" }).click();
  await expect(page.getByRole("heading", { name: "Documents" })).toBeVisible();
});