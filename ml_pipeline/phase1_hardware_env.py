#!/usr/bin/env python3
"""
Phase 1: Hardware Inspection & Isolated Environment Setup
Detects NVIDIA CUDA GPU VRAM, Apple Silicon Unified Memory, or system RAM,
selects the optimal 4-bit student base model, and configures an isolated Python
virtual environment with matching PyTorch, CUDA, Unsloth, TRL, and Hugging Face libraries.
"""

import json
import os
import platform
import shutil
import subprocess
import sys
from dataclasses import asdict, dataclass
from typing import List, Optional


@dataclass
class HardwareProfile:
    accelerator: str  # "cuda" | "mps" | "cpu"
    device_name: str
    memory_gb: float
    recommended_student_model: str
    lora_rank: int
    lora_alpha: int
    batch_size: int
    grad_accumulation: int
    learning_rate: str
    max_seq_length: int
    install_commands: List[str]


def _detect_nvidia_vram_gb() -> Optional[tuple[str, float]]:
    nvidia_smi = shutil.which("nvidia-smi")
    if not nvidia_smi:
        return None
    try:
        output = subprocess.check_output(
            [nvidia_smi, "--query-gpu=name,memory.total", "--format=csv,noheader,nounits"],
            text=True,
            stderr=subprocess.DEVNULL,
            timeout=5,
        ).strip()
        if not output:
            return None
        first_line = output.splitlines()[0]
        parts = [p.strip() for p in first_line.split(",")]
        if len(parts) >= 2:
            name = parts[0]
            vram_mb = float(parts[1])
            return name, round(vram_mb / 1024.0, 1)
    except Exception:
        return None
    return None


def _detect_apple_silicon_memory_gb() -> Optional[tuple[str, float]]:
    if platform.system() != "Darwin" or platform.machine() != "arm64":
        return None
    try:
        mem_bytes = int(
            subprocess.check_output(["sysctl", "-n", "hw.memsize"], text=True, timeout=5).strip()
        )
        brand = subprocess.check_output(
            ["sysctl", "-n", "machdep.cpu.brand_string"], text=True, timeout=5
        ).strip()
        return brand or "Apple Silicon", round(mem_bytes / (1024.0**3), 1)
    except Exception:
        return "Apple Silicon", 16.0


def _detect_system_ram_gb() -> float:
    try:
        if os.path.exists("/proc/meminfo"):
            with open("/proc/meminfo", "r", encoding="utf-8") as f:
                for line in f:
                    if line.startswith("MemTotal:"):
                        kb = int(line.split()[1])
                        return round(kb / (1024.0**2), 1)
    except Exception:
        pass
    return 16.0


def inspect_hardware(override_vram_gb: Optional[float] = None) -> HardwareProfile:
    """
    Inspects available GPU VRAM or Apple Silicon unified memory and selects a 4-bit
    student model and safe QLoRA training parameters.
    """
    cuda_info = _detect_nvidia_vram_gb()
    mac_info = _detect_apple_silicon_memory_gb()

    if override_vram_gb is not None:
        accelerator = "cuda"
        device_name = f"Configured GPU ({override_vram_gb:.1f} GB VRAM)"
        mem_gb = override_vram_gb
    elif cuda_info is not None:
        accelerator = "cuda"
        device_name, mem_gb = cuda_info
    elif mac_info is not None:
        accelerator = "mps"
        device_name, mem_gb = mac_info
    else:
        accelerator = "cpu"
        device_name = f"{platform.processor() or platform.machine() or 'System CPU'}"
        mem_gb = _detect_system_ram_gb()

    if mem_gb >= 22.0:
        student_model = "unsloth/Qwen2.5-14B-Instruct-bnb-4bit"
        lora_rank = 32
        lora_alpha = 32
        batch_size = 4
        grad_accum = 4
        lr = "1.5e-4"
        max_seq = 4096
    elif mem_gb >= 12.0:
        student_model = "unsloth/Qwen2.5-7B-Instruct-bnb-4bit"
        lora_rank = 16
        lora_alpha = 16
        batch_size = 2
        grad_accum = 4
        lr = "2e-4"
        max_seq = 2048
    else:
        student_model = "unsloth/Llama-3.2-3B-Instruct-bnb-4bit"
        lora_rank = 16
        lora_alpha = 16
        batch_size = 2
        grad_accum = 8
        lr = "2e-4"
        max_seq = 2048

    if accelerator == "mps":
        install_cmds = [
            f"{sys.executable} -m venv .venv",
            ".venv/bin/pip install --upgrade pip",
            ".venv/bin/pip install torch torchvision torchaudio",
            ".venv/bin/pip install mlx-lm datasets trl peft transformers accelerate pypdf python-docx",
        ]
    else:
        install_cmds = [
            f"{sys.executable} -m venv .venv",
            ".venv/bin/pip install --upgrade pip",
            '.venv/bin/pip install "unsloth[colab-new] @ git+https://github.com/unslothai/unsloth.git"',
            '.venv/bin/pip install --no-deps xformers "trl<0.9.0" peft accelerate bitsandbytes',
            ".venv/bin/pip install datasets pypdf python-docx openpyxl requests",
        ]

    return HardwareProfile(
        accelerator=accelerator,
        device_name=device_name,
        memory_gb=mem_gb,
        recommended_student_model=student_model,
        lora_rank=lora_rank,
        lora_alpha=lora_alpha,
        batch_size=batch_size,
        grad_accumulation=grad_accum,
        learning_rate=lr,
        max_seq_length=max_seq,
        install_commands=install_cmds,
    )


def bootstrap_virtualenv(venv_dir: str = ".venv", execute: bool = False) -> dict:
    profile = inspect_hardware()
    if execute:
        subprocess.check_call([sys.executable, "-m", "venv", venv_dir])
    return asdict(profile)


if __name__ == "__main__":
    hw = inspect_hardware()
    print(json.dumps(asdict(hw), indent=2))
