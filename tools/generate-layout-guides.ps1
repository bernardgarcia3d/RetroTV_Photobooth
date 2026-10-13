param(
    [string]$OutputDirectory = "D:/Codex/PHOTOBOOTH_PROJECT/layout-guides"
)

Add-Type -AssemblyName System.Drawing

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$paperWidth = 576
$margin = 40
$contentWidth = $paperWidth - ($margin * 2)
$headerHeight = 220
$footerHeight = 260
$gap = 12
$photoCellHeight = 280
$background = [System.Drawing.Color]::White
$ink = [System.Drawing.Color]::FromArgb(38, 48, 54)
$muted = [System.Drawing.Color]::FromArgb(93, 108, 114)
$photoFill = [System.Drawing.Color]::FromArgb(232, 242, 244)
$photoLine = [System.Drawing.Color]::FromArgb(0, 119, 136)
$safeFill = [System.Drawing.Color]::FromArgb(245, 240, 228)
$safeLine = [System.Drawing.Color]::FromArgb(171, 103, 35)
$guideRed = [System.Drawing.Color]::FromArgb(190, 47, 48)

function Add-Label {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [float]$X,
        [float]$Y,
        [float]$Size = 18,
        [System.Drawing.Color]$Color = $ink,
        [bool]$Bold = $false,
        [System.Drawing.StringAlignment]$Alignment = [System.Drawing.StringAlignment]::Near
    )
    $style = if ($Bold) { [System.Drawing.FontStyle]::Bold } else { [System.Drawing.FontStyle]::Regular }
    $font = New-Object System.Drawing.Font("Arial", $Size, $style)
    $brush = New-Object System.Drawing.SolidBrush($Color)
    $measure = $Graphics.MeasureString($Text, $font)
    $drawX = switch ($Alignment) {
        ([System.Drawing.StringAlignment]::Center) { $X - ($measure.Width / 2.0); break }
        ([System.Drawing.StringAlignment]::Far) { $X - $measure.Width; break }
        default { $X }
    }
    $drawY = $Y - ($measure.Height / 2.0)
    $Graphics.DrawString($Text, $font, $brush, [System.Drawing.PointF]::new([float]$drawX, [float]$drawY))
    $brush.Dispose(); $font.Dispose()
}

function Add-CenteredLabel {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [float]$CenterX,
        [float]$CenterY,
        [float]$Size = 18,
        [System.Drawing.Color]$Color = $ink,
        [bool]$Bold = $false
    )
    Add-Label -Graphics $Graphics -Text $Text -X $CenterX -Y $CenterY -Size $Size -Color $Color -Bold $Bold -Alignment ([System.Drawing.StringAlignment]::Center)
}

function Add-Box {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.RectangleF]$Rectangle,
        [System.Drawing.Color]$Fill,
        [System.Drawing.Color]$Line,
        [float]$Width = 2,
        [string]$Label = "",
        [System.Drawing.Color]$LabelColor = $Line
    )
    $fillBrush = New-Object System.Drawing.SolidBrush($Fill)
    $pen = New-Object System.Drawing.Pen($Line, $Width)
    $Graphics.FillRectangle($fillBrush, $Rectangle)
    $Graphics.DrawRectangle($pen, $Rectangle.X, $Rectangle.Y, $Rectangle.Width, $Rectangle.Height)
    if ($Label) {
        Add-CenteredLabel -Graphics $Graphics -Text $Label -CenterX ($Rectangle.X + ($Rectangle.Width / 2)) -CenterY ($Rectangle.Y + ($Rectangle.Height / 2)) -Size 16 -Color $LabelColor -Bold $true
    }
    $pen.Dispose(); $fillBrush.Dispose()
}

function New-Guide {
    param(
        [string]$FileName,
        [string]$Title,
        [string]$Subtitle,
        [int]$PhotoCount,
        [int]$Columns,
        [int]$Rows,
        [string]$PhotoRatio,
        [int]$PhotoCellHeight
    )

    $photoAreaHeight = ($Rows * $PhotoCellHeight) + (($Rows - 1) * $gap)
    $height = $headerHeight + $photoAreaHeight + $footerHeight
    $bitmap = New-Object System.Drawing.Bitmap($paperWidth, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $graphics.Clear($background)

    $paperPen = New-Object System.Drawing.Pen($ink, 3)
    $graphics.DrawRectangle($paperPen, 2, 2, $paperWidth - 5, $height - 5)
    $paperPen.Dispose()

    Add-Label -Graphics $graphics -Text $Title -X $margin -Y 34 -Size 26 -Color $ink -Bold $true
    Add-Label -Graphics $graphics -Text $Subtitle -X $margin -Y 78 -Size 11 -Color $muted
    Add-Label -Graphics $graphics -Text "RATIO $PhotoRatio" -X ($paperWidth - $margin) -Y 78 -Size 12 -Color $muted -Bold $true -Alignment ([System.Drawing.StringAlignment]::Far)

    $safeRect = [System.Drawing.RectangleF]::new([float]$margin, [float]108, [float]$contentWidth, [float]($headerHeight - 122))
    Add-Box -Graphics $graphics -Rectangle $safeRect -Fill $safeFill -Line $safeLine -Width 2 -Label "ARTWORK / HEADER SAFE AREA" -LabelColor $safeLine

    $cellWidth = ($contentWidth - (($Columns - 1) * $gap)) / $Columns
    for ($index = 0; $index -lt $PhotoCount; $index++) {
        $row = [math]::Floor($index / $Columns)
        $column = $index % $Columns
        $x = $margin + ($column * ($cellWidth + $gap))
        $y = $headerHeight + ($row * ($PhotoCellHeight + $gap))
        Add-Box -Graphics $graphics -Rectangle ([System.Drawing.RectangleF]::new([float]$x, [float]$y, [float]$cellWidth, [float]$PhotoCellHeight)) -Fill $photoFill -Line $photoLine -Width 3 -Label ("PHOTO " + ($index + 1)) -LabelColor $photoLine
    }

    $footerY = $headerHeight + $photoAreaHeight
    $footerRect = [System.Drawing.RectangleF]::new([float]$margin, [float]$footerY, [float]$contentWidth, [float]($footerHeight - 18))
    Add-Box -Graphics $graphics -Rectangle $footerRect -Fill $safeFill -Line $safeLine -Width 2 -Label "ARTWORK / FOOTER SAFE AREA" -LabelColor $safeLine

    Add-CenteredLabel -Graphics $graphics -Text "GUIDE ONLY — remove labels and colored boxes before export" -CenterX ($paperWidth / 2) -CenterY ($height - 10) -Size 11 -Color $guideRed -Bold $true

    $path = Join-Path $OutputDirectory $FileName
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Jpeg)
    $graphics.Dispose(); $bitmap.Dispose()
    return $path
}

New-Guide -FileName "layout-guide-1-photo.jpg" -Title "1 PHOTO STRIP GUIDE" -Subtitle "Landscape photo window" -PhotoCount 1 -Columns 1 -Rows 1 -PhotoRatio "16:9" -PhotoCellHeight 280
New-Guide -FileName "layout-guide-3-photo.jpg" -Title "3 PHOTO STRIP GUIDE" -Subtitle "Three landscape photo windows" -PhotoCount 3 -Columns 1 -Rows 3 -PhotoRatio "16:9" -PhotoCellHeight 280
New-Guide -FileName "layout-guide-4-photo.jpg" -Title "4 PHOTO STRIP GUIDE" -Subtitle "Two-column portrait crop grid" -PhotoCount 4 -Columns 2 -Rows 2 -PhotoRatio "4:5" -PhotoCellHeight 302
New-Guide -FileName "layout-guide-6-photo.jpg" -Title "6 PHOTO STRIP GUIDE" -Subtitle "Two-column portrait crop grid" -PhotoCount 6 -Columns 2 -Rows 3 -PhotoRatio "4:5" -PhotoCellHeight 302

Get-ChildItem -Path $OutputDirectory -Filter "layout-guide-*.jpg" | Select-Object Name, Length, FullName
