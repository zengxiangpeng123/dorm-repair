param(
    [string]$BaseUrl = $env:AI_BASE_URL,
    [string]$Model = $env:AI_MODEL,
    [string]$ApiKey = $env:AI_API_KEY,
    [int]$TimeoutSeconds = 60
)
$ErrorActionPreference = 'Stop'
function Fail([string]$message) { throw "ai-preflight: $message" }
if ([string]::IsNullOrWhiteSpace($BaseUrl)) { Fail 'AI_BASE_URL is empty' }
if ([string]::IsNullOrWhiteSpace($Model)) { Fail 'AI_MODEL is empty' }
if ([string]::IsNullOrWhiteSpace($ApiKey)) { Fail 'AI_API_KEY is empty' }
if ($BaseUrl -notmatch '^https?://') { Fail 'AI_BASE_URL must start with http:// or https://' }
if ($Model -notmatch '^[A-Za-z0-9._/-]+$') { Fail 'AI_MODEL contains unsupported characters' }
$url = $BaseUrl.TrimEnd('/') + '/chat/completions'
$payload = @{
    model = $Model
    messages = @(@{ role = 'user'; content = 'Reply with OK.' })
    temperature = 0
    max_tokens = 64
} | ConvertTo-Json -Depth 5 -Compress
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
try {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, $url)
    $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $ApiKey)
    $request.Content = [System.Net.Http.StringContent]::new($payload, [System.Text.Encoding]::UTF8, 'application/json')
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    if ($response.StatusCode -in 401, 403) { Fail 'configured key/model was rejected (HTTP 401/403)' }
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) { Fail ("configured key/model was rejected (HTTP {0})" -f [int]$response.StatusCode) }
    try { $json = $body | ConvertFrom-Json } catch { Fail 'gateway returned invalid JSON' }
    if (-not $json.choices -or $json.choices.Count -lt 1) { Fail 'response has no choices' }
    if ([string]$json.model -ne $Model) { Fail "gateway returned a different model: $($json.model)" }
    $message = $json.choices[0].message
    if ([string]::IsNullOrWhiteSpace([string]$message.content) -and [string]::IsNullOrWhiteSpace([string]$message.reasoning_content)) {
        Fail 'model returned no content'
    }

    $invalid = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, $url)
    $invalid.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', 'invalid-preflight-key')
    $invalid.Content = [System.Net.Http.StringContent]::new($payload, [System.Text.Encoding]::UTF8, 'application/json')
    $invalidResponse = $client.SendAsync($invalid).GetAwaiter().GetResult()
    $invalidStatus = [int]$invalidResponse.StatusCode
    if ($invalidStatus -notin 401, 403) { Fail "invalid key was not rejected with HTTP 401/403 (got $invalidStatus)" }
    Write-Host "AI gateway live probe: PASS (model=$Model)"
} catch {
    if ($_.Exception.Message -like 'ai-preflight:*') { throw }
    throw "ai-preflight: live request failed: $($_.Exception.Message)"
} finally {
    $client.Dispose()
}
