set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" ; cd ../.. > /dev/null 2>&1 && pwd -P)"
VENV_DIR=${PROJECT_DIR}/.venv-$(python -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}")')

if [ -d "${VENV_DIR}" ]; then
    echo "Using existing virtual environment at ${VENV_DIR}"
else
    echo "Creating virtual environment at ${VENV_DIR}"
    python -m venv "${VENV_DIR}"
fi

if [ -f "${VENV_DIR}/bin/activate" ]; then
    echo "Activating virtual environment"
    source "${VENV_DIR}/bin/activate"
else
    echo "Error: Virtual environment activation script not found at ${VENV_DIR}/bin/activate"
    exit 1
fi

pip install -r "${PROJECT_DIR}/deploy/ansible-requirements.txt"

# vi: ft=sh
