/*
 * Copyright (c) 2017, Holger Brandl, All rights reserved.
 *
 * Copyright (c) 2017, Vladimir Schneider, All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * Copyright (c) 2015-2017 Vladimir Schneider <vladimir.schneider@gmail.com>, all rights reserved.
 *
 * This code is private property of the copyright holder and cannot be used without
 * having obtained a license or prior written permission of the of the copyright holder.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package com.vladsch.flexmark.docx.converter.internal;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

public class ImageUtils {
    public static Image getImageFromClipboard() {
        Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        return getImageFromTransferable(transferable);
    }

    public static Image getImageFromTransferable(final Transferable transferable) {
        try {
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return (Image) transferable.getTransferData(DataFlavor.imageFlavor);
            } else {
                return null;
            }
        } catch (UnsupportedFlavorException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException();
        }

        return null;
    }

    public static BufferedImage scaleImage(BufferedImage sourceImage, int newWidth, int newHeight, int opType) {
        if (sourceImage == null) {
            return null;
        }

        if (newWidth == 0 || newHeight == 0) {
            return null;
        }

        AffineTransform at = AffineTransform.getScaleInstance(
                (double) newWidth / sourceImage.getWidth(null),
                (double) newHeight / sourceImage.getHeight(null)
        );

        //  http://nickyguides.digital-digest.com/bilinear-vs-bicubic.htm
        AffineTransformOp op = new AffineTransformOp(at, opType != 0 ? opType : AffineTransformOp.TYPE_BILINEAR);
        return op.filter(sourceImage, null);
    }

    public static BufferedImage toBufferedImage(Image src) {
        if (src instanceof BufferedImage) {
            return (BufferedImage) src;
        }

        int w = src.getWidth(null);
        int h = src.getHeight(null);
        if (w < 0 || h < 0) {
            return null;
        }

        int type = BufferedImage.TYPE_INT_ARGB;  // other options
        BufferedImage dest = new BufferedImage(w, h, type);
        //BufferedImage dest = UIUtil.createImage(w, h, type);
        Graphics2D g2 = dest.createGraphics();
        g2.drawImage(src, 0, 0, null);
        //UIUtil.drawImage(g2, src, 0, 0, null);
        g2.dispose();

        return dest;
    }

    public static void save(BufferedImage image, File file, String format) {
        try {
            ImageIO.write(image, format, file);  // ignore returned boolean
        } catch (Throwable e) {
            System.out.println("Write error for " + file.getPath() + ": " + e.getMessage());
        }
    }

    /**
     * @return Could be <code>null</code> if the image could not be read from the file (because of whatever strange
     * reason).
     */
    public static BufferedImage loadImageFromFile(File cachedImageFile) {
        if (cachedImageFile == null || !cachedImageFile.isFile()) {
            return null;
        }

        try {
            // related to http://bugs.java.com/bugdatabase/view_bug.do;jsessionid=dc84943191e06dffffffffdf200f5210dd319?bug_id=6967419
            for (int i = 0; i < 3; i++) {
                BufferedImage read = null;
                try {
                    read = ImageIO.read(cachedImageFile);
                } catch (IndexOutOfBoundsException e) {
                    System.err.print("*");
                    System.err.println("could not read" + cachedImageFile);
                    continue;
                }

                if (i > 0) System.err.println("");

                return read;
            }
        } catch (Throwable e) {
            //System.err.println("deleting " + cachedImageFile);
            //cachedImageFile.delete();
            return null;
        }

        return null;
    }

    public static BufferedImage loadImageFromURL(String imageURL) {
        try {
            return toBufferedImage(new ImageIcon(new URL(imageURL)).getImage());
        } catch (MalformedURLException ignored) {
        }

        return null;
    }

    /**
     * http://stackoverflow.com/questions/7603400/how-to-make-a-rounded-corner-image-in-java
     */
    public static BufferedImage makeRoundedCorner(BufferedImage image, int cornerRadius, int borderWidth) {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        //BufferedImage output = UIUtil.createImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = output.createGraphics();

        // This is what we want, but it only does hard-clipping, i.e. aliasing
        // g2.setClip(new RoundRectangle2D ...)

        // so instead fake soft-clipping by first drawing the desired clip shape
        // in fully opaque white with antialiasing enabled...
        g2.setComposite(AlphaComposite.Src);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, cornerRadius, cornerRadius));
        //        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
        //        g2.fillRect(0,0,256,256);

        // ... then compositing the image on top,
        // using the white shape from above as alpha source
        g2.setComposite(AlphaComposite.SrcAtop);
        g2.drawImage(image, 0, 0, null);
        //UIUtil.drawImage(g2, image, 0, 0, null);

        g2.dispose();
        //output.setRGB(3, 3, 123);
        return output;
    }

    public static BufferedImage addBorder(BufferedImage image, Color borderColor, int borderWidth, int cornerRadius) {
        int w = image.getWidth() + borderWidth * 2;
        int h = image.getHeight() + borderWidth * 2;
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        //BufferedImage output = UIUtil.createImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = output.createGraphics();
        g2.setColor(borderColor);
        g2.drawImage(image, borderWidth, borderWidth, null);
        //UIUtil.drawImage(g2, image, 0, 0, null);
        g2.setStroke(new BasicStroke(borderWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, borderWidth));
        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );
        if (cornerRadius > 0) {
            g2.drawRoundRect(borderWidth / 2, borderWidth / 2, w - borderWidth, h - borderWidth, cornerRadius, cornerRadius);
        } else {
            g2.drawRect(borderWidth / 2, borderWidth / 2, w - borderWidth, h - borderWidth);
        }
        g2.dispose();
        //output.setRGB(3, 3, 123);
        return output;
    }

    public static BufferedImage drawRectangle(BufferedImage image, int x, int y, int w, int h, Color borderColor, int borderWidth, int cornerRadius) {
        BufferedImage output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        //BufferedImage output = UIUtil.createImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = output.createGraphics();
        boolean invert = borderColor == null;

        if (invert) {
            // invert
            final int rgb = image.getRGB(x + w / 2, y + h / 2);
            borderColor = Color.getColor("", ~(rgb & 0xFFFFFF));
        }

        g2.drawImage(image, 0, 0, null);
        //UIUtil.drawImage(g2, image, 0, 0, null);
        g2.setStroke(new BasicStroke(borderWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, borderWidth));
        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        if (invert) {
            //g2.setXORMode(Color.WHITE);
        }

        g2.setColor(borderColor);
        if (cornerRadius > 0) {
            g2.drawRoundRect(x, y, w, h, cornerRadius, cornerRadius);
        } else {
            g2.drawRect(x, y, w, h);
        }
        g2.dispose();
        //output.setRGB(3, 3, 123);
        return output;
    }

    /**
     * http://stackoverflow.com/questions/2386064/how-do-i-crop-an-image-in-java
     */
    public static BufferedImage cropImage(BufferedImage image, int trimLeft, int trimRight, int trimTop, int trimBottom) {
        BufferedImage output = image.getSubimage(trimLeft, trimTop, image.getWidth() - trimLeft - trimRight, image.getHeight() - trimTop - trimBottom);
        return output;
    }

    /**
     * http://stackoverflow.com/questions/464825/converting-transparent-gif-png-to-jpeg-using-java
     */
    public static BufferedImage removeAlpha(BufferedImage image) {
        BufferedImage bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
        //BufferedImage bufferedImage = UIUtil.createImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
        Graphics g = bufferedImage.createGraphics();
        //Color.WHITE estes the background to white. You can use any other color
        g.drawImage(image, 0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), Color.WHITE, null);
        g.dispose();

        return bufferedImage;
    }

    /**
     * http://stackoverflow.com/questions/665406/how-to-make-a-color-transparent-in-a-bufferedimage-and-save-as-png
     */
    public static Image toTransparent(BufferedImage image, final Color color, final int tolerance) {
        //        ImageFilter filter = new RGBImageFilter() {
        //            public final int filterRGB(int x, int y, int rgb) {
        //                return (rgb << 8) & 0xFF000000;
        //            }
        //        };
        //
        //        ImageProducer ip = new FilteredImageSource(image.getSource(), filter);
        //        return toBufferedImage(Toolkit.getDefaultToolkit().createImage(ip));
        ImageFilter filter = new RGBImageFilter() {

            // the color we are looking for... Alpha bits are set to opaque
            public int markerRGB = color.getRGB() | 0xFF000000;
            int radius = tolerance * tolerance * 3;

            public final int filterRGB(int x, int y, int rgb) {
                if (tolerance == 0 && (rgb | 0xFF000000) == markerRGB) {
                    // Mark the alpha bits as zero - transparent
                    return 0x00FFFFFF & rgb;
                } else {
                    if ((rgb & 0xFF000000) == 0xFF000000) {

                        int delta1 = ((rgb & 0xFF0000) >> 16) - ((markerRGB & 0xFF0000) >> 16);
                        int delta2 = ((rgb & 0x00FF00) >> 8) - ((markerRGB & 0x00FF00) >> 8);
                        int delta3 = ((rgb & 0x0000FF)) - ((markerRGB & 0x0000FF));

                        int radDiff = delta1 * delta1 + delta2 * delta2 + delta3 * delta3;
                        if (radDiff <= radius) {
                            // Mark the alpha bits as zero - transparent
                            return 0x00FFFFFF & rgb;
                        }
                    }
                }
                return rgb;
            }
        };

        ImageProducer ip = new FilteredImageSource(image.getSource(), filter);
        return Toolkit.getDefaultToolkit().createImage(ip);
    }

    public static byte[] getImageBytes(BufferedImage image) {
        String imageString = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            ImageIO.write(image, "PNG", bos);
            byte[] imageBytes = bos.toByteArray();

            bos.close();
            return imageBytes;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
