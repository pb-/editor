develop:
	clojure -M:fig:build
.PHONY: develop

release:
	clojure -M -m cljs.main --optimizations advanced --compile-opts '{:infer-externs true, :closure-defines {"goog.DEBUG" false}}' -c editor.frontend
	ls -lh out/main.js
.PHONY: release
