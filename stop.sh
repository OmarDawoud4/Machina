#!/usr/bin/env bash
pkill -f 'target/machina-.*\.jar' && echo "[machina] nodes stopped" || echo "[machina] nothing running"
