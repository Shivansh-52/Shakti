"""
fix_pkl.py
==========
Binary patches the surakshasetu_safe_route_model.pkl to fix the
'STACK_GLOBAL requires str' error that occurs on Python 3.14.

The issue: joblib saved some module/class names as BYTES (opcode 0x42 or 0xC5)
instead of UNICODE strings (opcode 0x58 or 0x8C). Python 3.14 enforces
STACK_GLOBAL to only accept str, not bytes.

This script:
1. Scans the pkl for STACK_GLOBAL (0x93) opcodes
2. Finds the preceding SHORT_BINBYTES / BINBYTES push
3. Replaces them with SHORT_BINUNICODE / BINUNICODE equivalents
4. Saves the patched file

Usage:
    python fix_pkl.py
"""

import struct
import shutil

SRC = "surakshasetu_safe_route_model.pkl"
DST = "surakshasetu_safe_route_model_fixed.pkl"

# Opcode constants
PROTO         = 0x80
STACK_GLOBAL  = 0x93
MEMOIZE       = 0x94

# String opcodes (these are VALID for STACK_GLOBAL)
SHORT_BINUNICODE = 0x8C  # 1-byte length + utf-8 data
BINUNICODE       = 0x58  # 4-byte LE length + utf-8 data
BINUNICODE8      = 0x8D  # 8-byte LE length + utf-8 data

# Byte-string opcodes that SHOULD be strings (bad in old pickles)
SHORT_BINBYTES   = 0xC5  # 1-byte length + bytes
BINBYTES         = 0x42  # 4-byte LE length + bytes
SHORT_BINBYTES8  = 0x8E  # 8-byte LE length + bytes


def read_string_at(data, pos):
    """
    Given a position pointing to a string/bytes opcode,
    return (opcode, start, end, payload_bytes).
    end = index of the byte AFTER the last data byte.
    """
    op = data[pos]
    if op == SHORT_BINUNICODE or op == SHORT_BINBYTES:
        length = data[pos + 1]
        start = pos + 2
        return op, pos, start + length, data[start:start + length]
    elif op == BINUNICODE or op == BINBYTES:
        length = struct.unpack_from('<I', data, pos + 1)[0]
        start = pos + 5
        return op, pos, start + length, data[start:start + length]
    elif op == BINUNICODE8 or op == SHORT_BINBYTES8:
        length = struct.unpack_from('<Q', data, pos + 1)[0]
        start = pos + 9
        return op, pos, start + length, data[start:start + length]
    else:
        return None, pos, pos, b''


def make_unicode_push(text_bytes):
    """
    Encode text_bytes as a SHORT_BINUNICODE or BINUNICODE push.
    text_bytes must be valid UTF-8.
    """
    try:
        text_bytes.decode('utf-8')
    except UnicodeDecodeError:
        raise ValueError(f"Not valid UTF-8: {text_bytes!r}")
    
    length = len(text_bytes)
    if length <= 255:
        return bytes([SHORT_BINUNICODE, length]) + text_bytes
    else:
        return bytes([BINUNICODE]) + struct.pack('<I', length) + text_bytes


def patch(data: bytearray) -> tuple[bytearray, int]:
    """
    Scan data for STACK_GLOBAL preceded by bytes-pushes and fix them.
    Returns (patched_data, num_patches).
    """
    patches = 0
    i = 0
    result = bytearray()

    while i < len(data):
        b = data[i]

        # Look for MEMOIZE + STACK_GLOBAL sequence
        # Pattern: <str1_push> MEMOIZE <str2_push> MEMOIZE STACK_GLOBAL
        # or:      <str1_push> <str2_push> STACK_GLOBAL
        # We handle STACK_GLOBAL by looking back in result

        if b == STACK_GLOBAL:
            # The two items already on the stack are the LAST two pushes in result
            # We need to scan back through result to find them

            # Walk back skipping MEMOIZE
            pos = len(result) - 1
            while pos >= 0 and result[pos] == MEMOIZE:
                pos -= 1

            # pos now points to the last byte of the second push
            # We need to find start of second push, then start of first push

            # Try to identify what's at position going backward
            # Simplest: scan result for the two preceding string pushes
            converted_something = False

            # Instead of full backward parsing, re-encode the whole result up to here
            # by scanning forward for the STACK_GLOBAL context
            result.append(b)
            i += 1
            continue

        # Convert SHORT_BINBYTES → SHORT_BINUNICODE and BINBYTES → BINUNICODE
        # when they appear in module/class name context (before STACK_GLOBAL or MEMOIZE)
        if b in (SHORT_BINBYTES, BINBYTES, SHORT_BINBYTES8):
            op, start, end, payload = read_string_at(data, i)
            if op is not None:
                # Look ahead: after this and optional MEMOIZE, is there another
                # string push, optional MEMOIZE, then STACK_GLOBAL?
                lookahead = end
                if lookahead < len(data) and data[lookahead] == MEMOIZE:
                    lookahead += 1
                
                # Check for another string push
                op2, s2, e2, p2 = read_string_at(data, lookahead)
                if op2 is not None:
                    la2 = e2
                    if la2 < len(data) and data[la2] == MEMOIZE:
                        la2 += 1
                    if la2 < len(data) and data[la2] == STACK_GLOBAL:
                        # This IS a module/class pair for STACK_GLOBAL
                        # Convert this bytes push to unicode
                        new_push = make_unicode_push(payload)
                        result.extend(new_push)
                        patches += 1
                        i = end
                        continue
                
                # Also handle: this is the SECOND string (module already pushed)
                # Check backward: previous non-MEMOIZE was a bytes-push too
                # (we converted it above already)
                lookahead_sg = end
                if lookahead_sg < len(data) and data[lookahead_sg] == MEMOIZE:
                    lookahead_sg += 1
                if lookahead_sg < len(data) and data[lookahead_sg] == STACK_GLOBAL:
                    new_push = make_unicode_push(payload)
                    result.extend(new_push)
                    patches += 1
                    i = end
                    continue

        result.append(b)
        i += 1

    return bytearray(result), patches


def main():
    print(f"Reading {SRC} ({__import__('os').path.getsize(SRC):,} bytes)...")
    with open(SRC, 'rb') as f:
        data = bytearray(f.read())

    print("Patching bytes-typed module/class names before STACK_GLOBAL...")
    patched, n = patch(data)
    print(f"Applied {n} patches.")

    print(f"Writing {DST}...")
    with open(DST, 'wb') as f:
        f.write(patched)

    print("Testing load...")
    import pickle, warnings
    warnings.filterwarnings('ignore')
    with open(DST, 'rb') as f:
        model = pickle.load(f)
    print(f"✅ Success! Model type: {type(model).__name__}")

    # Quick predict test
    import pandas as pd, numpy as np
    from app.ml.model_loader import FEATURE_DEFAULTS, ALL_FEATURES
    row = dict(FEATURE_DEFAULTS)
    row['latitude_road'] = 28.6139
    row['longitude_road'] = 77.2090
    df = pd.DataFrame([row], columns=ALL_FEATURES)
    score = model.predict(df)[0]
    print(f"🎯 Test prediction: {score:.3f}")
    print(f"\n✅ Fixed pkl saved to: {DST}")
    print("   Update model_loader.py: change MODEL_PATH filename to surakshasetu_safe_route_model_fixed.pkl")


if __name__ == '__main__':
    import sys
    sys.path.insert(0, '.')
    main()
