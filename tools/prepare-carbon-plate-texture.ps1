[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Source,
    [Parameter(Mandatory=$true)][string]$Destination,
    [ValidateSet(16,32,64,128)][int]$Size = 64
)
# Asset-format conversion only: sample the generated artwork without repainting it.
# Keep the image_gen original separately; a standard sprite size avoids mip warnings.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$sourceImage=[Drawing.Bitmap]::new([IO.Path]::GetFullPath($Source))
$targetImage=[Drawing.Bitmap]::new($Size,$Size,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
try {
    for($pixelY=0;$pixelY -lt $Size;$pixelY++) {
        $sampleY=[Math]::Min($sourceImage.Height-1,[int][Math]::Floor(($pixelY+0.5)*$sourceImage.Height/$Size))
        for($pixelX=0;$pixelX -lt $Size;$pixelX++) {
            $sampleX=[Math]::Min($sourceImage.Width-1,[int][Math]::Floor(($pixelX+0.5)*$sourceImage.Width/$Size))
            $targetImage.SetPixel($pixelX,$pixelY,$sourceImage.GetPixel($sampleX,$sampleY))
        }
    }
    $targetImage.Save([IO.Path]::GetFullPath($Destination),[Drawing.Imaging.ImageFormat]::Png)
} finally {
    $targetImage.Dispose()
    $sourceImage.Dispose()
}
