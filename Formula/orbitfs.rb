class Orbitfs < Formula
  desc "OrbitFS CLI - Remote file management over TCP"
  homepage "https://github.com/Rishabh2804/OrbitFS"
  license "MIT"

  depends_on "openjdk" => "21+"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/Rishabh2804/OrbitFS/releases/download/v0.1.0/orbit-macos-arm64.jar"
      sha256 ""
    else
      url "https://github.com/Rishabh2804/OrbitFS/releases/download/v0.1.0/orbit-macos-x86_64.jar"
      sha256 ""
    end
  end

  def install
    libexec.install url.split("/").last => "orbit.jar"
    (bin/"orbit").write <<~EOS
      #!/bin/bash
      exec "#{Formula["openjdk"].opt_bin}/java" -jar "#{libexec}/orbit.jar" "$@"
    EOS
  end

  test do
    output = shell_output("#{bin}/orbit --help 2>&1")
    assert_match "OrbitFS CLI", output
  end
end