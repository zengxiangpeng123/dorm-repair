# Rebuild the requirement DOCX from its Markdown source and normalize page layout.
param([string]$Pandoc = 'pandoc')
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$source = Join-Path $root 'docs\01_需求\需求报告-V5.0.md'
$target = Join-Path $root 'docs\01_需求\需求报告-V5.0.docx'
& $Pandoc $source --from=gfm --to=docx --output=$target
if ($LASTEXITCODE -ne 0) { throw 'Pandoc failed to build the requirement DOCX' }
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Open($target, $false, $false)
    try {
        foreach ($section in $doc.Sections) {
            $section.PageSetup.PageWidth = 595.3
            $section.PageSetup.PageHeight = 841.9
            $section.PageSetup.LeftMargin = 56.7
            $section.PageSetup.RightMargin = 56.7
            $section.PageSetup.TopMargin = 56.7
            $section.PageSetup.BottomMargin = 56.7
        }
        $doc.Save()
        Write-Output "normalized A4 layout: $target"
    } finally { $doc.Close() }
} finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
}