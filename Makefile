image:
	docker build --build-arg version=$(shell git describe --dirty --always) -t editor .
.PHONY: image
