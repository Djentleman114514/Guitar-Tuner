#!/usr/bin/env python3
"""Create one persistent local signing identity; never overwrite an existing key."""
import os
from pathlib import Path
import secrets
import subprocess

root = Path(__file__).resolve().parent.parent
directory = root / ".signing"
properties = directory / "release.properties"
key = directory / "tuner-release.jks"
if properties.exists() or key.exists():
    raise SystemExit("Signing files already exist. Reuse and back them up; do not replace them.")
jdk = Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"))
directory.mkdir(mode=0o700, exist_ok=True)
directory.chmod(0o700)
password = secrets.token_urlsafe(32)
environment = os.environ.copy()
environment["TUNER_SIGNING_PASSWORD"] = password
subprocess.run([
    str(jdk / "bin/keytool"), "-genkeypair", "-keystore", str(key),
    "-storetype", "JKS", "-storepass:env", "TUNER_SIGNING_PASSWORD",
    "-keypass:env", "TUNER_SIGNING_PASSWORD", "-alias", "tuner",
    "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
    "-dname", "CN=Tuner, OU=Development, O=Xianzhun, C=CN",
], check=True, env=environment, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
key.chmod(0o600)
descriptor = os.open(properties, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(descriptor, "w") as stream:
    stream.write("storeFile=tuner-release.jks\nkeyAlias=tuner\n")
    stream.write(f"storePassword={password}\nkeyPassword={password}\n")
print("Local release signing identity created in .signing/. Back up both files securely.")
