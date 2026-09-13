class Orbitfs < Formula
  desc "OrbitFS CLI - Remote file management over TCP"
  homepage "https://github.com/Rishabh2804/OrbitFS"
  license "MIT"

  depends_on "openjdk" => "21+"

  on_macos do
    if Hardware::CPU.arm?
      sha256 ""
    else
      sha256 ""
    end
    url "https://github.com/Rishabh2804/OrbitFS/releases/download/v0.1.0/orbitfs-v0.1.0-macos.tar.gz"
    version "0.1.0"
  end

  def install
    (share_path = libexec/"share")
    share_path.install "orbit.jar"
    (bin/"orbit").write <<~EOS
      #!/bin/bash
      exec "#{Formula["openjdk"].opt_bin}/java" -jar "#{share_path}/orbit.jar" "$@"
    EOS
  end

  def post_install
    # Ensure JAVA_HOME is set for the wrapper
    openjdk = Formula("openjdk")
    ENV["JAVA_HOME"] = openjdk.opt_prefix
  end

  test do
    output = shell_output("#{bin}/orbit --help 2>&1")
    assert_match "OrbitFS CLI", output
  end
end