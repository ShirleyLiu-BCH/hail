.PHONY: jars clean

include env_var.mk
.DEFAULT_GOAL := shadowJar
MAKEFLAGS += --no-builtin-rules
.SUFFIXES:

REVISION := $(shell git rev-parse HEAD)
SHORT_REVISION := $(shell git rev-parse --short=12 HEAD)
BRANCH := $(shell git rev-parse --abbrev-ref HEAD)
URL := $(shell git config --get remote.origin.url)
SPARK_VERSION := 2.4.0
HAIL_MAJOR_MINOR_VERSION := 0.2
HAIL_PATCH_VERSION := 37
HAIL_PIP_VERSION := $(HAIL_MAJOR_MINOR_VERSION).$(HAIL_PATCH_VERSION)
HAIL_VERSION := $(HAIL_PIP_VERSION)-$(SHORT_REVISION)

$(eval $(call ENV_VAR,REVISION))
$(eval $(call ENV_VAR,SHORT_REVISION))
$(eval $(call ENV_VAR,BRANCH))
$(eval $(call ENV_VAR,URL))
$(eval $(call ENV_VAR,SPARK_VERSION))
$(eval $(call ENV_VAR,HAIL_PIP_VERSION))

HAIL_PYTHON3 ?= python3
PIP ?= $(HAIL_PYTHON3) -m pip

# not perfect, not robust to simdpp changes, but probably fine
JAR_SOURCES := $(shell git ls-files src/main)
JAR_SOURCES += build.gradle
JAR_TEST_SOURCES := $(shell git ls-files src/test)
PY_FILES := $(shell git ls-files python)

INIT_SCRIPTS := python/hailtop/hailctl/deploy.yaml
PYTHON_VERSION_INFO := python/hail/hail_version
PYTHON_VERSION_INFO += python/hail/hail_pip_version
PYTHON_VERSION_INFO += python/hailtop/hailctl/hail_version
PYTHON_VERSION_INFO += python/hail/docs/_static/hail_version.js
SCALA_BUILD_INFO := src/main/resources/build-info.properties

SHADOW_JAR := build/libs/hail-all-spark.jar
SHADOW_TEST_JAR := build/libs/hail-all-spark-test.jar
PYTHON_JAR := python/hail/backend/hail-all-spark.jar
WHEEL := build/deploy/dist/hail-$(HAIL_PIP_VERSION)-py3-none-any.whl

.PHONY: shadowJar
shadowJar: $(SHADOW_JAR)

GRADLE_ARGS += -Dspark.version=$(SPARK_VERSION) -Dpy4j.version$(PY4J_VERSION)

ifdef HAIL_COMPILE_NATIVES
$(SHADOW_JAR): native-lib-prebuilt
endif
$(SHADOW_JAR): $(SCALA_BUILD_INFO) $(JAR_SOURCES)
	./gradlew shadowJar $(GRADLE_ARGS)

ifdef HAIL_COMPILE_NATIVES
$(SHADOW_TEST_JAR): native-lib-prebuilt
endif
$(SHADOW_TEST_JAR): $(SCALA_BUILD_INFO) $(JAR_SOURCES) $(JAR_TEST_SOURCES)
	./gradlew shadowTestJar $(GRADLE_ARGS)

jars: $(SHADOW_JAR) $(SHADOW_TEST_JAR)

.PHONY: jvm-test
ifdef HAIL_COMPILE_NATIVES
jvm-test: native-lib-prebuilt
endif
jvm-test: $(SCALA_BUILD_INFO) $(JAR_SOURCES) $(JAR_TEST_SOURCES)
	+./pgradle test $(GRADLE_ARGS) $(GRADLE_TEST_ARGS)

src/main/resources/build-info.properties: env/REVISION env/SHORT_REVISION env/BRANCH env/URL
src/main/resources/build-info.properties: env/SPARK_VERSION env/HAIL_PIP_VERSION
src/main/resources/build-info.properties: Makefile
	echo '[Build Metadata]' > $@
	echo 'user=$(USER)' >> $@
	echo 'revision=$(REVISION)' >> $@
	echo 'branch=$(BRANCH)' >> $@
	echo 'date=$(shell date -u +%Y-%m-%dT%H:%M:%SZ)' >> $@
	echo 'url=$(URL)' >> $@
	echo 'sparkVersion=$(SPARK_VERSION)' >> $@
	echo 'hailPipVersion=$(HAIL_PIP_VERSION)' >> $@

.PHONY: python-version-info
python-version-info: $(PYTHON_VERSION_INFO)

python/hail/hail_version: env/SHORT_REVISION env/HAIL_PIP_VERSION
	echo $(HAIL_VERSION) > $@

python/hail/hail_pip_version: env/HAIL_PIP_VERSION
	echo $(HAIL_PIP_VERSION) > $@

python/hail/docs/_static/hail_version.js: python/hail/hail_version python/hail/hail_pip_version
	printf 'hail_version="$(shell cat python/hail/hail_version)";' > $@
	printf 'hail_pip_version="$(shell cat python/hail/hail_pip_version)"' >> $@

python/hailtop/hailctl/hail_version: python/hail/hail_version
	cp -f $< $@

python/README.md: ../README.md
	cp ../README.md python/

$(PYTHON_JAR): $(SHADOW_JAR)
	cp -f $< $@

.PHONY: pytest
pytest: $(PYTHON_VERSION_INFO) $(SHADOW_JAR) $(INIT_SCRIPTS)
pytest: python/README.md $(PYTHON_JAR)
	cd python && $(HAIL_PYTHON3) setup.py pytest \
     --addopts "-v \
     --color=no \
     --instafail \
     -r A \
     --self-contained-html \
     --html=../build/reports/pytest.html \
     $(PYTEST_ARGS)"

.PHONY: doctest
doctest: $(PYTHON_VERSION_INFO) $(SHADOW_JAR) $(INIT_SCRIPTS)
doctest: python/README.md $(PYTHON_JAR)
	cd python && $(HAIL_PYTHON3) setup.py pytest \
	  --addopts "-v \
    --color=no \
    --instafail \
    -r A \
    --self-contained-html \
    --html=../build/reports/pytest-doctest.html \
    --doctest-modules \
    --doctest-glob='*.rst' \
    --ignore=setup.py \
    --ignore=setup-hailtop.py \
    --ignore=hail/docs/conf.py \
    --ignore=cluster-tests \
    --ignore=hailtop \
    --ignore=test \
    --ignore=hail/docs/doctest_write_data.py \
    --ignore=hail/docs/getting_started_developing.rst \
    --ignore=hail/docs/getting_started.rst \
    $(PYTEST_ARGS)"

.PHONY: wheel
wheel: $(WHEEL)

$(WHEEL): $(PYTHON_VERSION_INFO) $(SHADOW_JAR) $(INIT_SCRIPTS) $(PY_FILES) $(PYTHON_JAR)
	rm -rf build/deploy
	mkdir -p build/deploy
	mkdir -p build/deploy/src
	cp ../README.md build/deploy/
	rsync -rv \
	    --exclude '__pycache__/' \
	    --exclude 'benchmark_hail/' \
	    --exclude 'docs/' \
	    --exclude 'dist/' \
	    --exclude 'test/' \
	    --exclude '*.log' \
	    python/ build/deploy/
	cd build/deploy; $(HAIL_PYTHON3) setup.py sdist bdist_wheel

# if the DEPLOY_REMOTE flag is not set, then deploy init scripts into a dev-username location
ifndef DEPLOY_REMOTE
DEV_CLARIFIER ?= $(shell whoami)-dev/
CLOUD_SUB_FOLDER := $(HAIL_PIP_VERSION)-$(SHORT_REVISION)
UPLOAD_RETENTION =
else
CLOUD_SUB_FOLDER := $(HAIL_PIP_VERSION)
UPLOAD_RETENTION = gsutil -m retention temp set "$(cloud_base)/*"
endif

HAILCTL_BUCKET_BASE ?= gs://hail-common/hailctl/dataproc

cloud_base := $(HAILCTL_BUCKET_BASE)/$(DEV_CLARIFIER)$(CLOUD_SUB_FOLDER)
wheel_cloud_path := $(cloud_base)/hail-$(HAIL_PIP_VERSION)-py3-none-any.whl
resources := $(wildcard python/hailtop/hailctl/dataproc/resources/*)
$(eval $(call ENV_VAR,cloud_base))
$(eval $(call ENV_VAR,wheel_cloud_path))

python/hailtop/hailctl/deploy.yaml: env/cloud_base env/wheel_cloud_path
python/hailtop/hailctl/deploy.yaml: $(resources) python/requirements.txt
	rm -f $@
	echo "dataproc:" >> $@
	for FILE in $(notdir $(resources)); do \
	  echo "  $$FILE: $(cloud_base)/$$FILE" >> $@ || exit 1; done
	echo "  wheel: $(wheel_cloud_path)" >> $@
	echo "  pip_dependencies: $(shell cat python/requirements.txt | grep -v pyspark | tr "\n" "|||")" >> $@

.PHONY: upload-artifacts
upload-artifacts: $(WHEEL)
	gsutil -m cp -r $(resources) $(WHEEL) $(cloud_base)
	gsutil -m acl set -r public-read $(cloud_base)
	$(UPLOAD_RETENTION)

.PHONY: install-editable
install-editable: $(PYTHON_VERSION_INFO) $(INIT_SCRIPTS)
install-editable: python/README.md $(PYTHON_JAR)
	-$(PIP) uninstall -y hail
	cd python && $(PIP) install -e .

.PHONY: install
install: $(WHEEL)
	-$(PIP) uninstall -y hail
	$(PIP) install $(WHEEL)

.PHONY: install-on-cluster
install-on-cluster: $(WHEEL)
	sed '/^pyspark/d' python/requirements.txt | xargs $(PIP) install -U
	-$(PIP) uninstall -y hail
	$(PIP) install $(WHEEL) --no-deps

.PHONY: install-hailctl
install-hailctl: install upload-artifacts

.PHONY: test-dataproc
test-dataproc: install-hailctl
	bash scripts/test-dataproc.sh

# use curl version >=7.55.0
# set DEPLOY_REMOTE to the hail-is/hail remote
# create an access token with full repo privileges at https://github.com/settings/tokens
# create a file that contains "Authorization: token YOUR_ACCESS_TOKEN_HERE"
# set GITHUB_OAUTH_HEADER_FILE to that filename
.PHONY: deploy
deploy: test-dataproc $(WHEEL)
	bash scripts/deploy.sh $(HAIL_PIP_VERSION) $(HAIL_VERSION) $(REVISION) $(DEPLOY_REMOTE) $(WHEEL) $(GITHUB_OAUTH_HEADER_FILE)

.PHONY: install-dev-deps
install-dev-deps:
	$(PIP) install -U -r python/dev-requirements.txt

.PHONY: install-deps
install-deps: install-dev-deps
	$(PIP) install -U -r python/requirements.txt

.PHONY: benchmark
benchmark: install
	HAIL_WHEEL=../hail/$(WHEEL) HAIL_VERSION=$(HAIL_PIP_VERSION) $(MAKE) -C ../benchmark benchmark

.PHONY: install-benchmark
install-benchmark:
	HAIL_WHEEL=DUMMY HAIL_VERSION=$(HAIL_PIP_VERSION) $(MAKE) -C ../benchmark install

.PHONY: clean-docs
clean-docs:
	$(MAKE) -C www clean

.PHONY: base-docs
base-docs: install-dev-deps
	mkdir -p build
	rm -rf build/www
	cp -R www build/www
	$(MAKE) -C build/www
	rm build/www/*.md

.PHONY: batch-docs
batch-docs: base-docs
	mkdir -p build/batch/docs
	rm -rf build/batch/docs
	cp -R python/hailtop/batch/docs build/batch/
	$(MAKE) -C build/batch/docs BUILDDIR=_build clean html
	mkdir -p build/www/docs
	rm -rf build/www/docs/batch
	mv build/batch/docs/_build/html build/www/docs/batch
	@echo Built Batch docs: build/www/docs/batch/index.html

HAIL_CACHE_VERSION = $(shell cat python/hail/hail_version)
.PHONY: hail-docs
hail-docs: install $(PYTHON_VERSION_INFO) base-docs
	mkdir -p build/docs
	rm -rf build/docs
	cp -R python/hail/docs build/
	cp python/hail/hail_version build/
	sed -E "s/\(hail\#([0-9]+)\)/(\[#\1](https:\/\/github.com\/hail-is\/hail\/pull\/\1))/g" \
    < build/docs/change_log.md \
    | pandoc -o build/docs/change_log.rst
	$(MAKE) SPHINXOPTS='-tchecktutorial' -C build/docs BUILDDIR=_build clean html
	mkdir -p build/www/docs
	rm -rf build/www/docs/$(HAIL_MAJOR_MINOR_VERSION)
	mv build/docs/_build/html build/www/docs/$(HAIL_MAJOR_MINOR_VERSION)
	find ./build/www -iname *.html -type f -exec sed -i -e "s/\.css/\.css\?v\=$(HAIL_CACHE_VERSION)/" {} +;
	@echo Built Hail docs: build/www/docs/$(HAIL_MAJOR_MINOR_VERSION)/index.html

.PHONY: upload-docs
upload-docs: hail-docs batch-docs
	cd build && tar czf www.tar.gz www
	gsutil -m cp build/www.tar.gz $(docs_location)
	gsutil -m retention temp set $(docs_location)
	gsutil -m acl set public-read $(docs_location)

.PHONY: test
test: pytest jvm-test doctest tutorial-test

.PHONY: tutorial-test
docs-no-test: install $(PYTHON_VERSION_INFO) install-dev-deps
	mkdir -p build
	rm -rf build/www
	cp -R www build/www
	rm -rf build/docs
	mkdir -p build/docs
	cp -R python/hail/docs build/
	cp python/hail/hail_version build/
	sed -E "s/\(hail\#([0-9]+)\)/(\[#\1](https:\/\/github.com\/hail-is\/hail\/pull\/\1))/g" \
    < build/docs/change_log.md \
    | pandoc -o build/docs/change_log.rst
	$(MAKE) -C build/www
	rm build/www/*.md
	$(MAKE) -C build/docs BUILDDIR=_build clean html
	mkdir -p build/www/docs
	rm -rf build/www/docs/$(HAIL_MAJOR_MINOR_VERSION)
	mv build/docs/_build/html build/www/docs/$(HAIL_MAJOR_MINOR_VERSION)
	find ./build/www -iname *.html -type f -exec sed -i -e "s/\.css/\.css\?v\=$(HAIL_CACHE_VERSION)/" {} +;
	@echo Built docs: build/www/docs/$(HAIL_MAJOR_MINOR_VERSION)/index.html

.PHONY: native-lib native-lib-test native-lib-clean native-lib-prebuilt native-lib-reset-prebuilt
native-lib:
	$(MAKE) -C src/main/c
native-lib-test:
	$(MAKE) -C src/main/c test
native-lib-clean:
	$(MAKE) -C src/main/c clean
native-lib-prebuilt:
	$(MAKE) -C src/main/c prebuilt
native-lib-reset-prebuilt:
	$(MAKE) -C src/main/c reset-prebuilt

clean: clean-env clean-docs native-lib-clean
	./gradlew clean $(GRADLE_ARGS)
	rm -rf build/
	rm -rf $(PYTHON_JAR)
	rm -rf python/README.md
	rm -rf $(SCALA_BUILD_INFO)
	rm -rf $(PYTHON_VERSION_INFO)

.PHONY: update-hail-repl
update-hail-repl: NAMESPACE ?= default
update-hail-repl: wheel
	kubectl -n $(NAMESPACE) cp $(WHEEL) $$(kubectl get pods -n $(NAMESPACE) -l app=hail-repl | tail -n +2 | awk '{print $$1}'):.
	kubectl -n $(NAMESPACE) exec -it $$(kubectl get pods -n $(NAMESPACE) -l app=hail-repl | tail -n +2 | awk '{print $$1}') -- pip3 install -U hail-$(HAIL_PIP_VERSION)-py3-none-any.whl
