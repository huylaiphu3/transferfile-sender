#!/bin/bash
#!/bin/bash
if command -v java &>/dev/null; then
    JAVA="java"
elif [ -x "$HOME/.jdks/corretto-21.0.11/bin/java" ]; then
    JAVA="$HOME/.jdks/corretto-21.0.11/bin/java"
elif [ -x "$HOME/.jdks/openjdk-26.0.1/bin/java" ]; then
    JAVA="$HOME/.jdks/openjdk-26.0.1/bin/java"
else
    echo "Khong tim thay java runtime."; exit 1
fi
"$JAVA" -cp out com.transferfile.Main "$@"
