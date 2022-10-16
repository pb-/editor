develop:
	clojure -A:fig:build
.PHONY: develop

release:
	clojure -M -m cljs.main --optimizations advanced -c editor.frontend
	ls -lh out/main.js
.PHONY: release
