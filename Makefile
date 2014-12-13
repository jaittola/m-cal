SHELL=/bin/bash
PD=resources/public
JSD=$(PD)/js
HANDLEBARS_TEMPLATES=$(wildcard $(PD)/templates/*.handlebars)

all: $(JSD)/bundle.min.js

clean:
	$(RM) $(JSD)/bundle.min.js $(JSD)/bundle.js

watch:
	watch make $(PD) --timeout=5

$(JSD)/bundle.min.js: $(JSD)/bundle.js
	uglifyjs $< -c -m > $@

$(JSD)/bundle.js: $(JSD)/bookings.js $(HANDLEBARS_TEMPLATES)
	browserify -t browserify-handlebars $< > $@

deps:
	npm install -g uglify-js browserify watch handlebars
	npm update

publish:
	@if [ -n "$$(git status --porcelain)" ] ; then \
            echo "You have uncommitted changes." && exit 1; \
        fi
	@if [[ ! "$$(git status -b --porcelain)" =~ '## master...' ]] ; then \
	    echo "Please publish from master branch." && exit 1; \
        fi
	git branch -D publish-to-heroku 2>&1 || true
	git checkout -b publish-to-heroku
	make all
	git add -f $(JSD)/bundle.min.js
	git rm -f package.json
	git commit -m "Setting up for packaging"
	git push -f heroku publish-to-heroku:master
	git checkout master
	git branch -D publish-to-heroku

run:
	lein ring server

.PHONY: all deps clean watch run publish
