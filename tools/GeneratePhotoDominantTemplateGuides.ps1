Add-Type -AssemblyName System.Drawing

$outputDirectory = Join-Path $PSScriptRoot '..\layout-guides\operator-template-guides-v3'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$layouts = @(
    # Two operator-owned top areas are kept above every photo block. The
    # photo windows retain their existing size; only the paper length grows.
    @{ Name = '1-photo-portrait'; Height = 1256; Columns = 1; Rows = 1; Ratio = 4.0 / 5.0; Top = 320; FooterBlocks = 2 },
    @{ Name = '3-photo-landscape'; Height = 1500; Columns = 1; Rows = 3; Ratio = 16.0 / 9.0; Top = 320; FooterBlocks = 1 },
    @{ Name = '4-photo-grid'; Height = 1252; Columns = 2; Rows = 2; Ratio = 4.0 / 5.0; Top = 320; FooterBlocks = 1 },
    @{ Name = '6-photo-grid'; Height = 1566; Columns = 2; Rows = 3; Ratio = 4.0 / 5.0; Top = 320; FooterBlocks = 1 }
)

$width = 576
$left = 18
$photoWidth = 540
$gap = 12

function New-Font([string] $family, [single] $size, [System.Drawing.FontStyle] $style = [System.Drawing.FontStyle]::Regular) {
    return [System.Drawing.Font]::new($family, $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

foreach ($layout in $layouts) {
    $bitmap = [System.Drawing.Bitmap]::new($width, $layout.Height, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::White)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    $ink = [System.Drawing.Color]::FromArgb(26, 29, 28)
    $muted = [System.Drawing.Color]::FromArgb(112, 118, 115)
    $accent = [System.Drawing.Color]::FromArgb(63, 87, 70)
    $frame = [System.Drawing.Color]::FromArgb(224, 232, 237)
    $operatorArea = [System.Drawing.Color]::FromArgb(252, 242, 218)
    $guide = [System.Drawing.Color]::FromArgb(118, 145, 159)
    $pen = [System.Drawing.Pen]::new($ink, 2)
    $thinPen = [System.Drawing.Pen]::new($muted, 1)
    $guidePen = [System.Drawing.Pen]::new($guide, 2)
    $guidePen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
    $titleFont = New-Font 'Arial' 22 ([System.Drawing.FontStyle]::Bold)
    $smallFont = New-Font 'Arial' 13
    $captionFont = New-Font 'Arial' 16 ([System.Drawing.FontStyle]::Bold)
    $photoLabelFont = New-Font 'Arial' 20 ([System.Drawing.FontStyle]::Bold)
    $subLabelFont = New-Font 'Arial' 12

    $formatCenter = [System.Drawing.StringFormat]::new()
    $formatCenter.Alignment = [System.Drawing.StringAlignment]::Center
    $formatCenter.LineAlignment = [System.Drawing.StringAlignment]::Center

    # These coloured regions show an operator where custom artwork belongs.
    # They are called top areas because they print before the photo block.
    $topAreaHeight = 128
    $topAreaGap = 12
    for ($topIndex = 0; $topIndex -lt 2; $topIndex++) {
        $topY = 20 + ($topIndex * ($topAreaHeight + $topAreaGap))
        $topRect = [System.Drawing.RectangleF]::new(18, $topY, 540, $topAreaHeight)
        $graphics.FillRectangle([System.Drawing.SolidBrush]::new($operatorArea), $topRect)
        $graphics.DrawRectangle($guidePen, $topRect.X, $topRect.Y, $topRect.Width, $topRect.Height)
        $topTitle = "OPERATOR TOP AREA $($topIndex + 1)"
        $topHint = if ($topIndex -eq 0) { 'Add logo • event name • date • short message' } else { 'Add sponsor logo • social handle • promotion • call to action' }
        $graphics.DrawString($topTitle, $titleFont, [System.Drawing.SolidBrush]::new($accent), [System.Drawing.RectangleF]::new(18, $topY + 24, 540, 30), $formatCenter)
        $graphics.DrawString($topHint, $smallFont, [System.Drawing.SolidBrush]::new($muted), [System.Drawing.RectangleF]::new(18, $topY + 66, 540, 24), $formatCenter)
        if ($topIndex -eq 1) {
            $graphics.DrawString($layout.Name.ToUpper(), $smallFont, [System.Drawing.SolidBrush]::new($ink), [System.Drawing.RectangleF]::new(18, $topY + 96, 540, 18), $formatCenter)
        }
    }

    $cellWidth = ($photoWidth - ($gap * ($layout.Columns - 1))) / $layout.Columns
    $cellHeight = $cellWidth / $layout.Ratio
    $photoBlockHeight = ($cellHeight * $layout.Rows) + ($gap * ($layout.Rows - 1))

    for ($index = 0; $index -lt ($layout.Columns * $layout.Rows); $index++) {
        $row = [Math]::Floor($index / $layout.Columns)
        $column = $index % $layout.Columns
        $x = $left + ($column * ($cellWidth + $gap))
        $y = $layout.Top + ($row * ($cellHeight + $gap))
        $rect = [System.Drawing.RectangleF]::new([single]$x, [single]$y, [single]$cellWidth, [single]$cellHeight)

        $graphics.FillRectangle([System.Drawing.SolidBrush]::new($frame), $rect)
        $graphics.DrawRectangle($guidePen, $rect.X, $rect.Y, $rect.Width, $rect.Height)
        $graphics.DrawString("PHOTO WINDOW $($index + 1)", $photoLabelFont, [System.Drawing.SolidBrush]::new($accent), $rect, $formatCenter)
        $graphics.DrawString("App fills this area • $([Math]::Round($cellWidth)) × $([Math]::Round($cellHeight)) dots", $subLabelFont, [System.Drawing.SolidBrush]::new($muted), [System.Drawing.RectangleF]::new($rect.X, $rect.Y + ($rect.Height / 2) + 16, $rect.Width, 20), $formatCenter)
    }

    $minimumFooterSpace = if ($layout.FooterBlocks -gt 1) { 230 } else { 126 }
    $footerTop = [Math]::Max($layout.Top + $photoBlockHeight + 20, $layout.Height - $minimumFooterSpace)
    $footerGap = if ($layout.FooterBlocks -gt 1) { 12 } else { 0 }
    $footerHeight = ($layout.Height - $footerTop - 18 - ($footerGap * ($layout.FooterBlocks - 1))) / $layout.FooterBlocks
    for ($footerIndex = 0; $footerIndex -lt $layout.FooterBlocks; $footerIndex++) {
        $footerY = $footerTop + ($footerIndex * ($footerHeight + $footerGap))
        $footerRect = [System.Drawing.RectangleF]::new(18, $footerY, 540, $footerHeight)
        $graphics.FillRectangle([System.Drawing.SolidBrush]::new($operatorArea), $footerRect)
        $graphics.DrawRectangle($guidePen, $footerRect.X, $footerRect.Y, $footerRect.Width, $footerRect.Height)
        $footerTitle = if ($layout.FooterBlocks -gt 1) { "OPERATOR FOOTER AREA $($footerIndex + 1)" } else { 'OPERATOR FOOTER AREA' }
        $footerHint = if ($footerIndex -eq 0) { 'Add note • QR / barcode • social handle • promotion' } else { 'Add a second message • sponsor logo • terms • call to action' }
        $graphics.DrawString($footerTitle, $captionFont, [System.Drawing.SolidBrush]::new($ink), [System.Drawing.RectangleF]::new(18, $footerY + 12, 540, 25), $formatCenter)
        $graphics.DrawString($footerHint, $smallFont, [System.Drawing.SolidBrush]::new($muted), [System.Drawing.RectangleF]::new(18, $footerY + 42, 540, 20), $formatCenter)
        $graphics.DrawString('Do not place design elements in blue photo windows.', $smallFont, [System.Drawing.SolidBrush]::new($accent), [System.Drawing.RectangleF]::new(18, $footerY + 60, 540, 20), $formatCenter)
    }

    $file = Join-Path $outputDirectory ("photo-dominant-$($layout.Name)-guide.jpg")
    $encoder = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
    $parameters = [System.Drawing.Imaging.EncoderParameters]::new(1)
    $parameters.Param[0] = [System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality, 96L)
    $bitmap.Save($file, $encoder, $parameters)

    $parameters.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
    $pen.Dispose()
    $thinPen.Dispose()
    $guidePen.Dispose()
    $titleFont.Dispose()
    $smallFont.Dispose()
    $captionFont.Dispose()
    $photoLabelFont.Dispose()
    $subLabelFont.Dispose()
    $formatCenter.Dispose()
}
