/**
 * PrimeFaces Extensions DynaForm Widget.
 *
 * @author Oleg Varaksin
 */
PrimeFacesExt.widget.DynaForm = PrimeFaces.widget.BaseWidget.extend({

    /**
     * Initializes the widget.
     *
     * @param {object} cfg The widget configuration.
     */
    init : function(cfg) {
        this._super(cfg);
        if (!cfg.isPostback) {
            this.toggledExtended = false;
        }

        if (cfg.autoSubmit && !window[cfg.widgetVar]) {
            this.submitForm();
        } else if (cfg.isPostback && this.toggledExtended) {
            var rows = this.jq.find("tr.pe-dynaform-extendedrow");
            if (rows.length > 0) {
                if (this.openExtended) {
                    rows.show();
                } else {
                    rows.hide();
                }
            }
        }
    },

    toggleExtended : function() {
        var rows = this.jq.find("tr.pe-dynaform-extendedrow");
        if (rows.length > 0) {
            rows.toggle();

            this.toggledExtended = true;
            this.openExtended = $(rows[0]).css("display") != "none";
        }
    },

    submitForm : function() {
        this.jq.find(":submit").trigger('click');
    }
});