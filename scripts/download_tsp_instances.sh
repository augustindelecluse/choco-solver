#!/bin/bash

# URL of the zip archive on the internet
pip install gdown  # for downloading on google drive
FILE_ID="16X_7CenOi9JDz3C3QfGdAZoYB8zCmE6D"

# Temporary path for downloading the zip file
TMP_ZIP_PATH="/tmp/tsplib.zip"

# Target directory for unzipping
TARGET_DIR="data/tsp/uncompressed"

# Download the zip file
echo "Downloading zip file..."
gdown --id $FILE_ID -O $TMP_ZIP_PATH

# Check if the target directory exists, create it if it doesn't
if [ ! -d "$TARGET_DIR" ]; then
    echo "Creating directory: $TARGET_DIR"
    mkdir -p "$TARGET_DIR"
fi

# Unzip the contents of the zip file into the target directory
echo "Unzipping $TMP_ZIP_PATH into $TARGET_DIR"
unzip -o "$TMP_ZIP_PATH" -d "$TARGET_DIR"

# Clean up the downloaded zip file
echo "Cleaning up..."
rm "$TMP_ZIP_PATH"

echo "Process completed."
