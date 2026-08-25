import re

path = 'src/main/java/net/datasa/tanoshimi/service/FileStorageService.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# Add imports for ImageIO
text = text.replace('import org.springframework.web.multipart.MultipartFile;', 
'''import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;''')

old_save = '''        try {
            Files.createDirectories(uploadDir);
            String filename = UUID.randomUUID() + "." + ext;
            Path target = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + filename;
        } catch (IOException e) {'''

new_save = '''        try {
            Files.createDirectories(uploadDir);
            String filename = UUID.randomUUID() + ".jpg"; // Force jpg for compression
            Path target = uploadDir.resolve(filename);
            
            // Image Resizing and Conversion
            BufferedImage original = ImageIO.read(file.getInputStream());
            if (original != null) {
                int originalWidth = original.getWidth();
                int originalHeight = original.getHeight();
                int targetWidth = originalWidth;
                int targetHeight = originalHeight;
                
                // Max width 800px
                if (originalWidth > 800) {
                    targetWidth = 800;
                    targetHeight = (int)((double)targetWidth / originalWidth * originalHeight);
                }
                
                // Need to strip alpha channel for JPEG
                BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = resized.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
                g.dispose();
                
                ImageIO.write(resized, "jpg", target.toFile());
            } else {
                // Not a valid image or failed to read, fallback to raw copy
                filename = UUID.randomUUID() + "." + ext;
                target = uploadDir.resolve(filename);
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            }
            
            return "/uploads/" + filename;
        } catch (IOException e) {'''

text = text.replace(old_save, new_save)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
print("FileStorageService patched for resizing!")