param(
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [string]$Question = "Summarize the uploaded document.",
    [int]$TaskPollCount = 40,
    [int]$TaskPollIntervalSeconds = 3,
    [switch]$RunOcrCheck,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.Web

$httpClient = [System.Net.Http.HttpClient]::new()
$httpClient.Timeout = [TimeSpan]::FromMinutes(3)
$httpClient.DefaultRequestHeaders.Accept.Clear()
$httpClient.DefaultRequestHeaders.Accept.ParseAdd("application/json")

function Write-Step {
    param([string]$Message)
    Write-Host "[smoke] $Message"
}

function ConvertTo-JsonBody {
    param([object]$Value)
    return [System.Net.Http.StringContent]::new(
        ($Value | ConvertTo-Json -Depth 8),
        [System.Text.Encoding]::UTF8,
        "application/json"
    )
}

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = $null,
        [string]$Accept = "application/json"
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::$Method,
        "$BaseUrl$Path"
    )
    $request.Headers.Accept.Clear()
    [void]$request.Headers.Accept.ParseAdd($Accept)

    if ($Token) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
    }
    if ($null -ne $Body) {
        $request.Content = ConvertTo-JsonBody -Value $Body
    }

    $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        throw "HTTP $([int]$response.StatusCode) $Path failed: $text"
    }

    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }
    return $text | ConvertFrom-Json
}

function Invoke-MultipartUpload {
    param(
        [string]$Path,
        [string]$FilePath,
        [string]$Token
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post,
        "$BaseUrl$Path"
    )
    $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)

    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $fileBytes = [System.IO.File]::ReadAllBytes($FilePath)
    $fileContent = [System.Net.Http.ByteArrayContent]::new($fileBytes)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse(
        [System.Web.MimeMapping]::GetMimeMapping($FilePath)
    )
    $multipart.Add($fileContent, "file", [System.IO.Path]::GetFileName($FilePath))
    $request.Content = $multipart

    $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        throw "HTTP $([int]$response.StatusCode) upload failed: $text"
    }
    return $text | ConvertFrom-Json
}

function Wait-IngestionTask {
    param(
        [string]$KnowledgeBaseId,
        [string]$TaskId,
        [string]$Token
    )

    for ($index = 0; $index -lt $TaskPollCount; $index++) {
        $result = Invoke-JsonRequest -Method Get -Path "/api/v1/knowledge-bases/$KnowledgeBaseId/ingestion-tasks" -Token $Token
        $task = $result.data | Where-Object { $_.id -eq $TaskId } | Select-Object -First 1
        if ($null -eq $task) {
            throw "Task $TaskId not found in knowledge base $KnowledgeBaseId"
        }

        Write-Step "Task $TaskId status=$($task.status) stage=$($task.currentStage)"
        if ($task.status -eq "SUCCEEDED") {
            return $task
        }
        if ($task.status -eq "FAILED") {
            throw "Task $TaskId failed: $($task.failureMessage)"
        }

        Start-Sleep -Seconds $TaskPollIntervalSeconds
    }

    throw "Task $TaskId did not finish within the polling window"
}

function Parse-SseText {
    param([string]$Text)

    $events = @()
    $blocks = $Text -replace "`r`n", "`n" -split "`n`n"
    foreach ($block in $blocks) {
        $trimmed = $block.Trim()
        if (-not $trimmed) {
            continue
        }

        $eventName = "message"
        $dataLines = New-Object System.Collections.Generic.List[string]
        foreach ($line in ($trimmed -split "`n")) {
            if ($line.StartsWith("event:")) {
                $eventName = $line.Substring(6).Trim()
            } elseif ($line.StartsWith("data:")) {
                [void]$dataLines.Add($line.Substring(5).Trim())
            }
        }

        $payloadText = [string]::Join("`n", $dataLines)
        $payload = $payloadText
        if ($payloadText) {
            try {
                $payload = $payloadText | ConvertFrom-Json
            } catch {
                $payload = $payloadText
            }
        }

        $events += [PSCustomObject]@{
            Event = $eventName
            Payload = $payload
        }
    }

    return $events
}

function Invoke-QaStream {
    param(
        [string]$KnowledgeBaseId,
        [string]$Token,
        [string]$Prompt
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post,
        "$BaseUrl/api/v1/knowledge-bases/$KnowledgeBaseId/qa/stream"
    )
    $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
    $request.Headers.Accept.Clear()
    [void]$request.Headers.Accept.ParseAdd("text/event-stream")
    $request.Content = ConvertTo-JsonBody -Value @{ query = $Prompt }

    $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        throw "HTTP $([int]$response.StatusCode) QA stream failed: $text"
    }

    return Parse-SseText -Text $text
}

function New-MinimalPdf {
    param([string]$Path)

    $pdf = @'
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Count 1 /Kids [3 0 R] >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj
4 0 obj
<< /Length 47 >>
stream
BT /F1 18 Tf 30 80 Td (OCR smoke PDF sample) Tj ET
endstream
endobj
5 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
xref
0 6
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000241 00000 n 
0000000338 00000 n 
trailer
<< /Size 6 /Root 1 0 R >>
startxref
408
%%EOF
'@
    [System.IO.File]::WriteAllText($Path, $pdf, [System.Text.Encoding]::ASCII)
}

if ($DryRun) {
    Write-Step "Base URL: $BaseUrl"
    Write-Step "Will verify /actuator/health, auth register, KB create, document upload, task polling, QA stream"
    if ($RunOcrCheck) {
        Write-Step "OCR smoke check enabled"
    }
    exit 0
}

$suffix = [guid]::NewGuid().ToString("N").Substring(0, 8)
$email = "smoke-$suffix@example.com"
$username = "smoke_$suffix"
$password = "SmokePass!123"
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "mykb-smoke-$suffix"
[System.IO.Directory]::CreateDirectory($tempRoot) | Out-Null

try {
    Write-Step "Checking actuator health"
    $health = Invoke-JsonRequest -Method Get -Path "/actuator/health"
    if ($health.status -ne "UP") {
        throw "Health endpoint is not UP: $($health | ConvertTo-Json -Depth 4)"
    }

    Write-Step "Registering smoke user $email"
    $register = Invoke-JsonRequest -Method Post -Path "/api/v1/auth/register" -Body @{
        username = $username
        email = $email
        password = $password
    }
    $token = $register.data.accessToken
    if (-not $token) {
        throw "Smoke register returned no access token"
    }

    Write-Step "Creating knowledge base"
    $kb = Invoke-JsonRequest -Method Post -Path "/api/v1/knowledge-bases" -Token $token -Body @{
        name = "smoke-kb-$suffix"
        description = "Smoke validation knowledge base"
    }
    $knowledgeBaseId = $kb.data.id
    if (-not $knowledgeBaseId) {
        throw "Knowledge base creation returned no id"
    }

    $txtPath = Join-Path $tempRoot "smoke.txt"
    [System.IO.File]::WriteAllText(
        $txtPath,
        "This is the smoke test document for My Knowledge Base. It proves upload and QA.",
        [System.Text.Encoding]::UTF8
    )

    Write-Step "Uploading text document"
    $upload = Invoke-MultipartUpload -Path "/api/v1/knowledge-bases/$knowledgeBaseId/documents" -FilePath $txtPath -Token $token
    $textTaskId = $upload.data.ingestionTask.id
    if (-not $textTaskId) {
        throw "Text upload returned no task id"
    }
    [void](Wait-IngestionTask -KnowledgeBaseId $knowledgeBaseId -TaskId $textTaskId -Token $token)

    Write-Step "Calling QA stream"
    $events = Invoke-QaStream -KnowledgeBaseId $knowledgeBaseId -Token $token -Prompt $Question
    if (-not ($events | Where-Object { $_.Event -eq "done" })) {
        throw "QA stream did not emit a done event"
    }

    if ($RunOcrCheck) {
        $pdfPath = Join-Path $tempRoot "smoke.pdf"
        New-MinimalPdf -Path $pdfPath
        Write-Step "Uploading PDF document for OCR smoke"
        $pdfUpload = Invoke-MultipartUpload -Path "/api/v1/knowledge-bases/$knowledgeBaseId/documents" -FilePath $pdfPath -Token $token
        $pdfTaskId = $pdfUpload.data.ingestionTask.id
        if (-not $pdfTaskId) {
            throw "PDF upload returned no task id"
        }
        [void](Wait-IngestionTask -KnowledgeBaseId $knowledgeBaseId -TaskId $pdfTaskId -Token $token)
    }

    Write-Step "Smoke test passed"
} finally {
    if (Test-Path $tempRoot) {
        Remove-Item $tempRoot -Recurse -Force
    }
    $httpClient.Dispose()
}
