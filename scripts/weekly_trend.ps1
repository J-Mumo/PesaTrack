$dir = 'C:\Users\JOEL\Downloads\pesatrackstatistics\6-17'
$installs = Import-Csv (Join-Path $dir 'install base.csv') | ForEach-Object {
  [pscustomobject]@{ Date=[datetime]::ParseExact($_.Date,'d MMM yyyy',[Globalization.CultureInfo]::InvariantCulture); IB=[int]$_.'Install base (All devices, Unique devices, Per interval, Daily): All countries / regions' }
}
$devs = Import-Csv (Join-Path $dir 'device acquisition.csv') | ForEach-Object {
  [pscustomobject]@{ Date=[datetime]::ParseExact($_.Date,'d MMM yyyy',[Globalization.CultureInfo]::InvariantCulture); Dev=[int]$_.'Device acquisition (All devices, All events, Per interval, Daily): All countries / regions' }
}
$ibByDate = @{}; foreach ($r in $installs) { $ibByDate[$r.Date] = $r.IB }
$devByDate = @{}; foreach ($r in $devs) { $devByDate[$r.Date] = $r.Dev }

$weeks = @(
  @{n='1-7 May  (post-v1.3.0)';    s=[datetime]'2026-05-01'; e=[datetime]'2026-05-07'},
  @{n='8-14 May (post-v1.3.0)';    s=[datetime]'2026-05-08'; e=[datetime]'2026-05-14'},
  @{n='15-21 May (post-v1.3.0)';   s=[datetime]'2026-05-15'; e=[datetime]'2026-05-21'},
  @{n='22-28 May (post-v1.3.0)';   s=[datetime]'2026-05-22'; e=[datetime]'2026-05-28'},
  @{n='29 May-4 Jun (v1.3.1)';     s=[datetime]'2026-05-29'; e=[datetime]'2026-06-04'},
  @{n='5-11 Jun (v1.3.2)';         s=[datetime]'2026-06-05'; e=[datetime]'2026-06-11'},
  @{n='12-14 Jun (v1.3.2)';        s=[datetime]'2026-06-12'; e=[datetime]'2026-06-14'}
)

Write-Host "Week                              Installs  IB-start  IB-end  Net   Uninst  Uninst%"
Write-Host "----                              --------  --------  ------  ----  ------  -------"
foreach ($w in $weeks) {
  $installSum = 0
  for ($d = $w.s; $d -le $w.e; $d = $d.AddDays(1)) {
    if ($devByDate.ContainsKey($d)) { $installSum += $devByDate[$d] }
  }
  $prevDay = $w.s.AddDays(-1)
  $ibStart = if ($ibByDate.ContainsKey($prevDay)) { $ibByDate[$prevDay] } else { $ibByDate[$w.s] }
  $ibEnd = $ibByDate[$w.e]
  $net = $ibEnd - $ibStart
  $uninst = $installSum - $net
  $pct = if ($installSum -gt 0) { [math]::Round(($uninst/$installSum)*100,1) } else { 0 }
  $line = '{0,-34} {1,8} {2,9} {3,7} {4,5} {5,7} {6,7}%' -f $w.n,$installSum,$ibStart,$ibEnd,$net,$uninst,$pct
  Write-Host $line
}
