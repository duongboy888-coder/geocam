# GeoCam – Camera in định vị, tên, ngày giờ lên ảnh

## Tính năng
- In lên ảnh: tiêu đề tuỳ chỉnh, các dòng tuỳ chỉnh (`Nhãn: giá trị`), ngày giờ, địa chỉ, toạ độ GPS, ghi chú
- Bản đồ nhỏ (OpenStreetMap) có ghim vị trí, logo riêng ở góc trên phải
- Khi không có mạng: vẫn in ngày giờ và toạ độ (bỏ địa chỉ và bản đồ)
- Ghi GPS và giờ chụp vào EXIF, ảnh lưu ở `DCIM/GeoCam`
- Camera trước/sau, ảnh xoay đúng chiều khi chụp ngang
- Android 10 trở lên

## Build bằng GitHub Actions (từ Termux)
```bash
cd /sdcard/GeoCam        # thư mục đã giải nén
git init -b main
git add .
git commit -m "GeoCam v1"
gh repo create geocam --public --source=. --push
```
Sau đó vào tab **Actions** của repo, mở lần build mới nhất và tải artifact **GeoCam-apk**.

Nếu git báo "dubious ownership" khi chạy trong /sdcard:
`git config --global --add safe.directory '*'`
