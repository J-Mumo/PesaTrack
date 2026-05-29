$dir = "C:\Users\JOEL\Downloads\pesatrackstatistics"
$map = @{
  "All Android versions (1).csv"                     = "Avg Rating"
  "All Android versions, Android 11.csv"             = "Crashes"
  "All Android versions.csv"                         = "ANRs"
  "All countries _ regions, Kenya.csv"               = "7-day retention"
  "All countries _ regions, Kenya, Tanzania.csv"     = "Installed audience"
  "All countries _ regions, Kenya, Tanzania (1).csv" = "User loss"
  "All countries _ regions, Kenya, Tanzania (2).csv" = "Install base"
  "All countries _ regions, Kenya, Tanzania (3).csv" = "Install events"
  "All countries _ regions, Kenya, Tanzania (4).csv" = "Uninstall events"
  "All countries _ regions, Kenya, Tanzania (5).csv" = "Store listing impressions"
  "All countries _ regions, Kenya, Tanzania (6).csv" = "Daily active devices"
  "All countries _ regions, Kenya, Tanzania (7).csv" = "Daily active users"
  "All countries _ regions, Kenya, Tanzania (8).csv" = "Returning users"
  "All countries _ regions, Kenya, Tanzania (9).csv" = "Store listing acquisitions"
}
foreach ($k in $map.Keys) {
  $path = Join-Path $dir $k
  $rows = Import-Csv $path
  $cols = $rows[0].PSObject.Properties.Name | Where-Object { $_ -ne "Date" -and $_ -ne "Notes" }
  Write-Host "===== $($map[$k]) ====="
  Write-Host "Rows: $($rows.Count)  Range: $($rows[0].Date) -> $($rows[-1].Date)"
  foreach ($c in $cols) {
    $nums = @()
    $nonzeroDates = @()
    foreach ($r in $rows) {
      $v = $r.$c
      if ($v -ne "-" -and $v -ne "" -and $null -ne $v) {
        try {
          $n = [double]$v
          $nums += $n
          if ($n -ne 0) { $nonzeroDates += "$($r.Date)=$n" }
        } catch {}
      }
    }
    if ($nums.Count -gt 0) {
      $sum = ($nums | Measure-Object -Sum).Sum
      $max = ($nums | Measure-Object -Maximum).Maximum
      $label = ($c -split ":")[-1].Trim()
      Write-Host ("  [{0}] count={1} sum={2} max={3}" -f $label, $nums.Count, $sum, $max)
      if ($nonzeroDates.Count -gt 0 -and $nonzeroDates.Count -le 30) {
        Write-Host ("    nonzero: " + ($nonzeroDates -join ", "))
      }
    } else {
      $label = ($c -split ":")[-1].Trim()
      Write-Host "  [$label] no numeric data"
    }
  }
}
