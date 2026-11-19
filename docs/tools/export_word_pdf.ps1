# Export reviewed DOCX files to PDF using the installed Microsoft Word.
# Keeps DOCX/PDF content and page fields synchronized without editing binary files manually.
param(
    [Parameter(Mandatory=$true)][string[]]$Paths
)
$ErrorActionPreference = 'Stop'
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    foreach ($item in $Paths) {
        $input = (Resolve-Path $item).Path
        $doc = $word.Documents.Open($input, $false, $false)
        try {
            $doc.Fields.Update() | Out-Null
            foreach ($toc in $doc.TablesOfContents) { $toc.Update() | Out-Null }
            $doc.Repaginate()
            foreach ($section in $doc.Sections) {
                $section.Footers.Item(1).Range.Fields.Update() | Out-Null
            }
            $doc.Save()
            $pdf = [System.IO.Path]::ChangeExtension($input, '.pdf')
            $doc.ExportAsFixedFormat($pdf, 17)
            Write-Output "exported $pdf"
        } finally {
            $doc.Close()
        }
    }
} finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
}