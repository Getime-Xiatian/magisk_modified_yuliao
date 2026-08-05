#!/system/bin/sh
#######################################################################################
# White List Downloader
#
# Downloads the remote su whitelist and installs it at /data/adb/magisk/white_list,
# where magiskd reads it on every su request.
#
# Format of the remote file: one package name per line, '#' starts a comment.
# The remote list only ADDS root access; it never removes the hardcoded
# whitelist (com.mi.xttechsettings + andro.pluginsuite always keep root).
#######################################################################################

WL_URL="https://raw.giteeusercontent.com/getime_1/magisk_modified/raw/master/white_list.txt"
WL_DST="/data/adb/magisk/white_list"

BB="/data/adb/magisk/busybox"
[ -x "$BB" ] || BB=$(command -v busybox)
[ -x "$BB" ] || exit 0

# Network may not be ready yet at late_start; retry for up to 6 minutes.
for i in 1 2 3 4 5 6; do
  if "$BB" wget -q -T 20 -O "$WL_DST.tmp" "$WL_URL" 2>/dev/null; then
    if [ -s "$WL_DST.tmp" ]; then
      mv "$WL_DST.tmp" "$WL_DST"
      chmod 644 "$WL_DST"
      break
    fi
  fi
  rm -f "$WL_DST.tmp"
  sleep 60
done

exit 0
