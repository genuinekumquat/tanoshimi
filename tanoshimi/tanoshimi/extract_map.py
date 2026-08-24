import cv2
import numpy as np

img = cv2.imread('src/main/resources/static/assets/japan_map.png')
gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

# Threshold to separate the white/colored regions from black lines.
# Assuming black lines are ~0, we want them as 0 and regions as 255.
# If background is white and regions are colored/white, black lines separate them.
_, thresh = cv2.threshold(gray, 200, 255, cv2.THRESH_BINARY_INV) 
# wait, if lines are black, thresholding at 200 inverse: white becomes black (0), black lines become white (255)
# then we want the contours of the lines? Or invert again?
# Let's just do Canny edges.
edges = cv2.Canny(gray, 50, 150)
kernel = np.ones((5,5), np.uint8)
edges = cv2.dilate(edges, kernel, iterations=2)
regions_img = cv2.bitwise_not(edges) # lines are black, regions are white

contours, hierarchy = cv2.findContours(regions_img, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)

res = []
sorted_contours = sorted(contours, key=cv2.contourArea, reverse=True)

h, w = gray.shape
min_area = h * w * 0.01 # at least 1% of the image

valid_contours = []
# skip the largest one which is usually the background
for i, c in enumerate(sorted_contours):
    area = cv2.contourArea(c)
    if area > min_area:
        epsilon = 0.005 * cv2.arcLength(c, True)
        approx = cv2.approxPolyDP(c, epsilon, True)
        
        M = cv2.moments(approx)
        if M['m00'] != 0:
            cx = int(M['m10']/M['m00'])
            cy = int(M['m01']/M['m00'])
            
            pts = []
            for p in approx:
                nx = round((p[0][0] / w) * 100, 2)
                ny = round((p[0][1] / h) * 100, 2)
                pts.append(f"{nx},{ny}")
                
            path_str = " ".join(pts)
            valid_contours.append({
                'cx': cx, 'cy': cy, 'path': path_str, 'area': area
            })

with open('svg_paths.txt', 'w') as f:
    f.write(f"Found {len(valid_contours)} regions:\n")
    for i, c in enumerate(valid_contours):
        f.write(f'Area {i}: cx={c["cx"]}, cy={c["cy"]}, area={c["area"]}\n')
        f.write(f'<polygon id="region-{i}" points="{c["path"]}" class="explore-region-box" stroke="transparent" stroke-width="0" fill="transparent" />\n')

