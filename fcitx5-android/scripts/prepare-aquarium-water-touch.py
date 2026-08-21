#!/usr/bin/env python3
"""Extract four low-latency 16-bit touch samples from BigSoundBank sound 1384."""

import argparse
import struct
import wave
from pathlib import Path


EVENT_ONSETS_SECONDS = (0.165, 1.595, 3.030, 4.460)
LEAD_SECONDS = 0.012
DURATION_SECONDS = 0.280
FADE_IN_SECONDS = 0.001
FADE_OUT_SECONDS = 0.085


def decode_pcm24_le(chunk: bytes) -> list[int]:
    values = []
    for index in range(0, len(chunk), 3):
        value = chunk[index] | chunk[index + 1] << 8 | chunk[index + 2] << 16
        if value & 0x800000:
            value -= 1 << 24
        values.append(value)
    return values


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="48 kHz mono 24-bit WAV source")
    parser.add_argument("output", type=Path, help="Android res/raw output directory")
    args = parser.parse_args()

    with wave.open(str(args.source), "rb") as source:
        if (source.getnchannels(), source.getsampwidth(), source.getframerate()) != (1, 3, 48_000):
            raise ValueError("expected mono 48 kHz 24-bit PCM WAV")
        samples = decode_pcm24_le(source.readframes(source.getnframes()))
        sample_rate = source.getframerate()

    args.output.mkdir(parents=True, exist_ok=True)
    frame_count = round(DURATION_SECONDS * sample_rate)
    fade_in_frames = round(FADE_IN_SECONDS * sample_rate)
    fade_out_frames = round(FADE_OUT_SECONDS * sample_rate)
    for number, onset in enumerate(EVENT_ONSETS_SECONDS, start=1):
        start = round((onset - LEAD_SECONDS) * sample_rate)
        segment = samples[start:start + frame_count]
        pcm16 = bytearray()
        for index, value in enumerate(segment):
            envelope = min(1.0, index / max(fade_in_frames, 1))
            remaining = len(segment) - 1 - index
            envelope *= min(1.0, remaining / max(fade_out_frames, 1))
            converted = round(value / 256 * envelope * 0.90)
            converted = max(-32768, min(32767, converted))
            pcm16 += struct.pack("<h", converted)
        destination = args.output / f"aquarium_water_touch_{number}.wav"
        with wave.open(str(destination), "wb") as target:
            target.setnchannels(1)
            target.setsampwidth(2)
            target.setframerate(sample_rate)
            target.writeframes(pcm16)


if __name__ == "__main__":
    main()
