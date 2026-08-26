$dir = "C:\Users\JOEL\Downloads\pesatrackstatistics\6-17"

function Parse-Series {
  param($path, $cols)
  $rows = Import-Csv $path
  $out = @()
  foreach ($r in $rows) {
    $obj = [ordered]@{ Date = [datetime]::ParseExact($r.Date,'d MMM yyyy',[Globalization.CultureInfo]::InvariantCulture) }
    foreach ($k in $cols.Keys) {
      $raw = $r.($cols[$k])
      if ($raw -eq '-' -or $raw -eq '' -or $null -eq $raw) { $obj[$k] = $null }
      elseif ($raw -match '%$') { $obj[$k] = [double]($raw -replace '%','') }
      else { try { $obj[$k] = [double]$raw } catch { $obj[$k] = $null } }
    }
    $out += [pscustomobject]$obj
  }
  return $out
}

$installBase = Parse-Series (Join-Path $dir 'install base.csv') @{All='Install base (All devices, Unique devices, Per interval, Daily): All countries / regions'; KE='Install base (All devices, Unique devices, Per interval, Daily): Kenya'; TZ='Install base (All devices, Unique devices, Per interval, Daily): Tanzania'}
$audience = Parse-Series (Join-Path $dir 'installed-audience-time series.csv') @{All='Installed audience (All users, Unique users, Per interval, Daily): All countries / regions'; KE='Installed audience (All users, Unique users, Per interval, Daily): Kenya'; TZ='Installed audience (All users, Unique users, Per interval, Daily): Tanzania'}
$firstOpens = Parse-Series (Join-Path $dir 'first opens.csv') @{All='First opens (Per interval, Daily): All countries / regions'; KE='First opens (Per interval, Daily): Kenya'; TZ='First opens (Per interval, Daily): Tanzania'}
$acq = Parse-Series (Join-Path $dir 'all user acquisitions.csv') @{All='User acquisition (All users, All events, Per interval, Daily): All countries / regions'; KE='User acquisition (All users, All events, Per interval, Daily): Kenya'; TZ='User acquisition (All users, All events, Per interval, Daily): Tanzania'}
$devAcq = Parse-Series (Join-Path $dir 'device acquisition.csv') @{All='Device acquisition (All devices, All events, Per interval, Daily): All countries / regions'; KE='Device acquisition (All devices, All events, Per interval, Daily): Kenya'; TZ='Device acquisition (All devices, All events, Per interval, Daily): Tanzania'}
$dau = Parse-Series (Join-Path $dir 'daily active users.csv') @{All='Daily active users (DAU) (Unique users, Per interval, Daily): All countries / regions'; KE='Daily active users (DAU) (Unique users, Per interval, Daily): Kenya'}
$mau = Parse-Series (Join-Path $dir 'monthly active users.csv') @{All='Monthly active users (MAU) (Unique users, Per interval, Daily): All countries / regions'; KE='Monthly active users (MAU) (Unique users, Per interval, Daily): Kenya'}
$ret = Parse-Series (Join-Path $dir 'returning users.csv') @{All='Returning users (Daily): All countries / regions'; KE='Returning users (Daily): Kenya'}
$ret7 = Parse-Series (Join-Path $dir '7 day retention.csv') @{All='7-day retention (Per interval, Daily): All countries / regions'; KE='7-day retention (Per interval, Daily): Kenya'}
$visitors = Parse-Series (Join-Path $dir 'store listing visitors.csv') @{All='Store listing visitors (All users, Unique users, Per interval, Daily): All countries / regions'; KE='Store listing visitors (All users, Unique users, Per interval, Daily): Kenya'}
$convRate = Parse-Series (Join-Path $dir 'store listing conversion rate.csv') @{All='Store listing conversion rate (All users, Daily): All countries / regions'; KE='Store listing conversion rate (All users, Daily): Kenya'}
$listAcq = Parse-Series (Join-Path $dir 'store listing acquisitions.csv') @{All='Store listing acquisitions (All users, Unique users, Per interval, Daily): All countries / regions'; KE='Store listing acquisitions (All users, Unique users, Per interval, Daily): Kenya'}

function Window-Stats {
  param($series, $start, $end, $field='All')
  $sub = $series | Where-Object { $_.Date -ge $start -and $_.Date -le $end }
  $vals = $sub | ForEach-Object { $_.$field } | Where-Object { $null -ne $_ }
  if ($vals.Count -eq 0) { return @{ days=$sub.Count; n=0; sum=0; avg=0; max=0; min=0 } }
  $sum = ($vals | Measure-Object -Sum).Sum
  $avg = ($vals | Measure-Object -Average).Average
  $max = ($vals | Measure-Object -Maximum).Maximum
  $min = ($vals | Measure-Object -Minimum).Minimum
  @{ days=$sub.Count; n=$vals.Count; sum=[math]::Round($sum,2); avg=[math]::Round($avg,2); max=$max; min=$min }
}

$w1 = @{name='Post-v1.3.0 baseline (1-28 May)'; s=[datetime]'2026-05-01'; e=[datetime]'2026-05-28'}
$w2 = @{name='v1.3.1 era (29 May-1 Jun)'; s=[datetime]'2026-05-29'; e=[datetime]'2026-06-01'}
$w3 = @{name='v1.3.2 era (2 Jun-14 Jun)'; s=[datetime]'2026-06-02'; e=[datetime]'2026-06-14'}

Write-Host "===== INSTALL BASE (snapshot per day) ====="
foreach ($w in @($w1,$w2,$w3)) {
  $last = ($installBase | Where-Object { $_.Date -ge $w.s -and $_.Date -le $w.e -and $null -ne $_.All } | Select-Object -Last 1)
  $first = ($installBase | Where-Object { $_.Date -ge $w.s -and $_.Date -le $w.e -and $null -ne $_.All } | Select-Object -First 1)
  if ($last -and $first) {
    Write-Host ("  {0}: start={1} end={2} delta={3} (KE: {4} -> {5})" -f $w.name,$first.All,$last.All,($last.All-$first.All),$first.KE,$last.KE)
  }
}

Write-Host "`n===== INSTALLED AUDIENCE (users) ====="
foreach ($w in @($w1,$w2,$w3)) {
  $last = ($audience | Where-Object { $_.Date -ge $w.s -and $_.Date -le $w.e -and $null -ne $_.All } | Select-Object -Last 1)
  $first = ($audience | Where-Object { $_.Date -ge $w.s -and $_.Date -le $w.e -and $null -ne $_.All } | Select-Object -First 1)
  if ($last -and $first) {
    Write-Host ("  {0}: start={1} end={2} delta={3} (KE: {4} -> {5})" -f $w.name,$first.All,$last.All,($last.All-$first.All),$first.KE,$last.KE)
  }
}

Write-Host "`n===== DAILY ACTIVE USERS ====="
foreach ($w in @($w1,$w2,$w3)) {
  $s = Window-Stats $dau $w.s $w.e 'All'
  Write-Host ("  {0}: avg={1} max={2} days_with_data={3}/{4}" -f $w.name,$s.avg,$s.max,$s.n,$s.days)
}

Write-Host "`n===== MONTHLY ACTIVE USERS ====="
foreach ($w in @($w1,$w2,$w3)) {
  $last = $mau | Where-Object { $_.Date -ge $w.s -and $_.Date -le $w.e -and $null -ne $_.All } | Select-Object -Last 1
  $first = $mau | Where-Object { $_.Date -ge $w.s -and $_.Date -le $w.e -and $null -ne $_.All } | Select-Object -First 1
  if ($last) { Write-Host ("  {0}: start={1} end={2} delta={3} (KE end={4})" -f $w.name,$first.All,$last.All,($last.All-$first.All),$last.KE) }
}

Write-Host "`n===== FIRST OPENS (sum) ====="
foreach ($w in @($w1,$w2,$w3)) {
  $s = Window-Stats $firstOpens $w.s $w.e 'All'
  Write-Host ("  {0}: total={1} avg/day={2} max={3}" -f $w.name,$s.sum,$s.avg,$s.max)
}

Write-Host "`n===== USER ACQUISITIONS (sum) ====="
foreach ($w in @($w1,$w2,$w3)) {
  $s = Window-Stats $acq $w.s $w.e 'All'
  Write-Host ("  {0}: total={1} avg/day={2}" -f $w.name,$s.sum,$s.avg)
}

Write-Host "`n===== DEVICE ACQUISITIONS (sum) ====="
foreach ($w in @($w1,$w2,$w3)) {
  $s = Window-Stats $devAcq $w.s $w.e 'All'
  Write-Host ("  {0}: total={1} avg/day={2}" -f $w.name,$s.sum,$s.avg)
}

Write-Host "`n===== RETURNING USERS % (avg of days with data) ====="
foreach ($w in @($w1,$w2,$w3)) {
  $s = Window-Stats $ret $w.s $w.e 'All'
  Write-Host ("  {0}: avg={1}% min={2}% max={3}% days={4}/{5}" -f $w.name,$s.avg,$s.min,$s.max,$s.n,$s.days)
}

Write-Host "`n===== 7-DAY RETENTION ====="
foreach ($w in @($w1,$w2,$w3)) {
  $nz = $ret7 | Where-Object { $_.Date -ge $w.s -and $_.Date -le $w.e -and $null -ne $_.All -and $_.All -gt 0 }
  Write-Host ("  {0}: nonzero_days={1}" -f $w.name,$nz.Count)
  if ($nz) { Write-Host ("    " + (($nz | ForEach-Object { "$(($_.Date).ToString('d MMM'))={0}%" -f $_.All }) -join ', ')) }
}

Write-Host "`n===== STORE LISTING ====="
foreach ($w in @($w1,$w2,$w3)) {
  $v = Window-Stats $visitors $w.s $w.e 'All'
  $c = Window-Stats $convRate $w.s $w.e 'All'
  $la = Window-Stats $listAcq $w.s $w.e 'All'
  Write-Host ("  {0}: visitors={1} conv_rate_avg={2}% acq={3}" -f $w.name,$v.sum,$c.avg,$la.sum)
}

Write-Host "`n===== LATEST SNAPSHOT (14 Jun / 10 Jun for DAU/MAU) ====="
$ibLast = $installBase | Where-Object { $_.Date -eq [datetime]'2026-06-14' }
$auLast = $audience | Where-Object { $_.Date -eq [datetime]'2026-06-14' }
$dauLast = $dau | Where-Object { $_.Date -eq [datetime]'2026-06-10' }
$mauLast = $mau | Where-Object { $_.Date -eq [datetime]'2026-06-10' }
Write-Host ("  Install base    : All={0} KE={1} TZ={2}" -f $ibLast.All,$ibLast.KE,$ibLast.TZ)
Write-Host ("  Audience(users) : All={0} KE={1} TZ={2}" -f $auLast.All,$auLast.KE,$auLast.TZ)
Write-Host ("  DAU (10 Jun)    : All={0} KE={1}" -f $dauLast.All,$dauLast.KE)
Write-Host ("  MAU (10 Jun)    : All={0} KE={1}" -f $mauLast.All,$mauLast.KE)
$dauMauPct = if ($mauLast.All -gt 0) { [math]::Round(($dauLast.All / $mauLast.All) * 100,1) } else { 0 }
Write-Host ("  DAU/MAU stickiness: {0}%" -f $dauMauPct)

Write-Host "`n===== RECENT 30-DAY UNINSTALL APPROXIMATION ====="
# unique users acquired in last 30 days minus net audience change
$acq30 = ($acq | Where-Object { $_.Date -ge [datetime]'2026-05-15' -and $_.Date -le [datetime]'2026-06-14' } | ForEach-Object { $_.All } | Where-Object { $null -ne $_ } | Measure-Object -Sum).Sum
$audStart = ($audience | Where-Object { $_.Date -eq [datetime]'2026-05-15' }).All
$audEnd = ($audience | Where-Object { $_.Date -eq [datetime]'2026-06-14' }).All
$netGain = $audEnd - $audStart
$uninstalls = $acq30 - $netGain
$uninstallRatio = if ($acq30 -gt 0) { [math]::Round(($uninstalls/$acq30)*100,1) } else { 0 }
Write-Host ("  Acquired 15 May-14 Jun: {0}" -f $acq30)
Write-Host ("  Audience: {0} -> {1} (net +{2})" -f $audStart,$audEnd,$netGain)
Write-Host ("  Implied uninstalls in window: {0}  ({1}% uninstall ratio)" -f $uninstalls,$uninstallRatio)
