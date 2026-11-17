param([string]$Path, [switch]$Verify, [switch]$ExportPdf)
$ErrorActionPreference = 'Stop'
$Path = (Resolve-Path -LiteralPath $Path).Path
$x = New-Object -ComObject Excel.Application
$x.Visible = $false
$x.DisplayAlerts = $false
$x.AskToUpdateLinks = $false
$x.AutomationSecurity = 3
try {
  $wb = $x.Workbooks.Open($Path, 0, $false)
  if ($wb.ReadOnly) { throw 'El archivo está abierto o bloqueado; no se guardará una copia silenciosamente.' }
  $x.CalculateFullRebuild()
  if ($null -ne $x.CircularReference) { throw 'Excel detectó una referencia circular.' }
  $wb.Save()
  if ($ExportPdf) {
    $pdfPath = Join-Path (Split-Path $Path) 'Ruma_inversionistas_2026-11.pdf'
    $wb.Worksheets.Item('Inversionistas').ExportAsFixedFormat(0, $pdfPath)
    Write-Output "PDF: $pdfPath"
  }
  $wb.Close($false)
  Write-Output 'recalculated'
  if ($Verify) {
    $refs = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'modelo_referencias.json') -Raw | ConvertFrom-Json
    $testDir = Join-Path (Split-Path $Path) 'verificacion'
    New-Item -ItemType Directory -Path $testDir -Force | Out-Null
    $cases = @(
      @{ Name='rentable'; Changes=@{ pr_act0=2500; pr_g=0.10; pl_kg0=30; pl_kgF=30; muni_fee=10000 } },
      @{ Name='sin_dividendos'; Changes=@{ pr_act0=2500; pr_g=0.10; pl_kg0=30; pl_kgF=30; muni_fee=10000; payout=0; exit_on=0 } },
      @{ Name='venta'; Changes=@{ pr_act0=2500; pr_g=0.10; pl_kg0=30; pl_kgF=30; muni_fee=10000; payout=0; exit_on=1 } },
      @{ Name='perdidas_b'; Changes=@{ pl_kgF=30; muni_fee=5000; ir_sis=2 } },
      @{ Name='capital_insuficiente'; Changes=@{ inversion=1; inv_pct=0.2 } }
    )
    foreach ($case in $cases) {
      $testPath = Join-Path $testDir ($case.Name + '.xlsx')
      Copy-Item -LiteralPath $Path -Destination $testPath -Force
      $testBook = $x.Workbooks.Open($testPath, 0, $false)
      try {
        $assumptions = $testBook.Worksheets.Item('Supuestos')
        foreach ($key in $case.Changes.Keys) {
          $cellRef = $refs.inputs.$key
          if (-not $cellRef) { throw "No existe el supuesto $key" }
          $assumptions.Range($cellRef).Value2 = [double]$case.Changes[$key]
        }
        $x.CalculateFullRebuild()
        if ($null -ne $x.CircularReference) { throw "Referencia circular: $($case.Name)" }
        $testBook.Save()
      } finally { $testBook.Close($false) }
      Write-Output "verified calculation: $($case.Name)"
    }
  }
} finally {
  $x.Quit()
  [System.Runtime.InteropServices.Marshal]::ReleaseComObject($x) | Out-Null
}
