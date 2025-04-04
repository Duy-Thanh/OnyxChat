module.exports = (sequelize, DataTypes) => {
  const Contact = sequelize.define('Contact', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    contactId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    nickname: {
      type: DataTypes.STRING,
      allowNull: true
    },
    blocked: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    }
  }, {
    tableName: 'contacts',
    timestamps: true,
    indexes: [
      {
        unique: true,
        fields: ['userId', 'contactId']
      }
    ]
  });

  Contact.associate = (models) => {
    Contact.belongsTo(models.User, {
      foreignKey: 'userId',
      as: 'user'
    });

    Contact.belongsTo(models.User, {
      foreignKey: 'contactId',
      as: 'contact'
    });
  };

  return Contact;
}; 